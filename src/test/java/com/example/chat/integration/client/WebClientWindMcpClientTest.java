package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.WindServerType;
import com.example.chat.common.exception.FinancialProviderException;
import com.example.chat.config.WindProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Wind MCP 原生客户端测试。
 */
public class WebClientWindMcpClientTest {

    /** 模拟 Wind MCP 服务。 */
    private MockWebServer mockWebServer;
    /** 测试客户端。 */
    private WebClientWindMcpClient windMcpClient;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WindProperties properties = new WindProperties();
        WindCredentialProvider credentialProvider = () -> Optional.of("test-api-key");
        windMcpClient = new WebClientWindMcpClient(
                WebClient.builder(), properties, credentialProvider,
                serverType -> mockWebServer.url("/").toString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testCallInitializesThenParsesSseAndNormalizesInvalid() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse(initializeResult()));
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: " + toolCallResult() + "\n\n"));

        JSONObject arguments = new JSONObject(true);
        arguments.put("windcode", "600519.SH");
        WindToolCallRequest request = WindToolCallRequest.builder()
                .serverType(WindServerType.STOCK_DATA)
                .toolName("get_stock_price_indicators")
                .arguments(arguments)
                .build();

        StepVerifier.create(windMcpClient.call(request))
                .assertNext(result -> {
                    JSONObject contentData = JSON.parseObject(result.getContent().get(0).getText());
                    JSONArray rows = contentData.getJSONObject("data").getJSONArray("rows");
                    assertEquals(null, rows.getJSONArray(0).get(1));
                    assertEquals("元", contentData.getJSONObject("data").getString("unit"));
                    assertEquals("万得 Wind 金融数据服务", result.getSource());
                    assertEquals(2, result.getWarnings().size());
                    assertEquals("BACKEND_INVALID_AS_NULL",
                            result.getWarnings().get(1).getString("code"));
                })
                .verifyComplete();

        RecordedRequest initializeRequest = mockWebServer.takeRequest(1L, TimeUnit.SECONDS);
        RecordedRequest callRequest = mockWebServer.takeRequest(1L, TimeUnit.SECONDS);
        assertNotNull(initializeRequest);
        assertNotNull(callRequest);
        JSONObject initializeBody = parseRequestBody(initializeRequest);
        JSONObject callBody = parseRequestBody(callRequest);
        assertEquals("initialize", initializeBody.getString("method"));
        assertEquals("tools/call", callBody.getString("method"));
        assertEquals("Bearer test-api-key", callRequest.getHeader("Authorization"));
        JSONObject callParams = callBody.getJSONObject("params");
        assertEquals("get_stock_price_indicators", callParams.getString("name"));
        assertEquals("600519.SH", callParams.getJSONObject("arguments").getString("windcode"));
    }

    @Test
    public void testListToolsFiltersToolsOutsideServerAllowlist() {
        mockWebServer.enqueue(jsonResponse(initializeResult()));
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"tools\":["
                + "{\"name\":\"get_financial_news\",\"description\":\"新闻\",\"inputSchema\":{}},"
                + "{\"name\":\"unsafe_tool\",\"description\":\"非法\",\"inputSchema\":{}}]}}";
        mockWebServer.enqueue(jsonResponse(body));

        StepVerifier.create(windMcpClient.listTools(WindServerType.FINANCIAL_DOCS))
                .assertNext(result -> {
                    assertEquals(1, result.getTools().size());
                    assertEquals("get_financial_news", result.getTools().get(0).getName());
                })
                .verifyComplete();
    }

    @Test
    public void testMissingCredentialFailsBeforeNetworkCall() {
        WebClientWindMcpClient clientWithoutCredential = new WebClientWindMcpClient(
                WebClient.builder(), new WindProperties(), Optional::empty,
                serverType -> mockWebServer.url("/").toString());

        StepVerifier.create(clientWithoutCredential.listTools(WindServerType.STOCK_DATA))
                .expectErrorSatisfies(throwable -> assertProviderError(
                        throwable, FinancialProviderErrorCode.AUTHENTICATION_FAILED))
                .verify();
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    public void testRateLimitIsStructuredAndNotRetried() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));

        StepVerifier.create(windMcpClient.listTools(WindServerType.STOCK_DATA))
                .expectErrorSatisfies(throwable -> assertProviderError(
                        throwable, FinancialProviderErrorCode.RATE_LIMITED))
                .verify();
        assertEquals(1, mockWebServer.getRequestCount());
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String initializeResult() {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{"
                + "\"protocolVersion\":\"2025-03-26\",\"capabilities\":{}}}";
    }

    private String toolCallResult() {
        JSONObject data = new JSONObject(true);
        data.put("unit", "元");
        JSONArray row = new JSONArray();
        row.add("600519.SH");
        row.add("INVALID");
        row.add(100);
        JSONArray rows = new JSONArray();
        rows.add(row);
        data.put("rows", rows);
        data.put("excelTotalCount", 99);
        JSONObject businessPayload = new JSONObject(true);
        businessPayload.put("data", data);

        JSONObject contentItem = new JSONObject(true);
        contentItem.put("type", "text");
        contentItem.put("text", businessPayload.toJSONString());
        JSONArray content = new JSONArray();
        content.add(contentItem);
        JSONObject result = new JSONObject(true);
        result.put("content", content);
        JSONObject envelope = new JSONObject(true);
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", "2");
        envelope.put("result", result);
        return envelope.toJSONString();
    }

    private JSONObject parseRequestBody(RecordedRequest request) {
        return JSON.parseObject(request.getBody().readUtf8());
    }

    private void assertProviderError(Throwable throwable, FinancialProviderErrorCode expectedCode) {
        assertNotNull(throwable);
        FinancialProviderException exception = (FinancialProviderException) throwable;
        assertEquals(expectedCode, exception.getErrorCode());
    }
}
