package com.example.chat.agent.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.config.AiRoutingProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Responses 协议客户端集成测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
public class OpenAiResponsesClientTest {

    private static final String API_KEY = "test-api-key";
    private static final String MODEL = "gpt-test";

    private MockWebServer mockWebServer;
    private OpenAiResponsesClient responsesClient;

    /**
     * 初始化模拟模型供应商。
     *
     * @throws IOException 模拟服务启动失败
     */
    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        AiRoutingProperties.ProviderProperties providerProperties =
                AiRoutingProperties.ProviderProperties.builder()
                        .apiKey(API_KEY)
                        .model(MODEL)
                        .responsesPath(OpenAiResponsesClient.DEFAULT_RESPONSES_PATH)
                        .responseTimeout(Duration.ofSeconds(5))
                        .build();
        responsesClient = new OpenAiResponsesClient("openai", webClient, providerProperties);
    }

    /**
     * 关闭模拟模型供应商。
     *
     * @throws IOException 模拟服务关闭失败
     */
    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * 验证类型化文本、用量事件、认证头和请求结构。
     *
     * @throws InterruptedException 获取请求被中断
     */
    @Test
    public void testTypedTextUsageAndAuthorization() throws InterruptedException {
        JSONObject delta = new JSONObject(true);
        delta.put("response_id", "resp_1");
        delta.put("delta", "你好");
        mockWebServer.enqueue(sseResponse(
                sseEvent("response.output_text.delta", delta)
                        + sseEvent("response.completed", createCompletedWithUsage())
                        + "data: [DONE]\n\n"));

        AgentModelRequest request = AgentModelRequest.fromMessages(
                List.of(AgentMessage.user("你好")),
                Collections.emptyList(),
                "session-1");

        StepVerifier.create(responsesClient.respond(request))
                .assertNext(event -> {
                    assertEquals(AgentModelEvent.EventType.OUTPUT_TEXT_DELTA, event.getType());
                    assertEquals("你好", event.getTextDelta());
                    assertEquals("resp_1", event.getResponseId());
                })
                .assertNext(event -> {
                    assertEquals(AgentModelEvent.EventType.USAGE, event.getType());
                    assertEquals(12L, event.getUsage().getInputTokens());
                    assertEquals(5L, event.getUsage().getOutputTokens());
                    assertEquals(17L, event.getUsage().getTotalTokens());
                    assertEquals(4L, event.getUsage().getCachedInputTokens());
                    assertEquals(2L, event.getUsage().getReasoningTokens());
                })
                .assertNext(event ->
                        assertEquals(AgentModelEvent.EventType.COMPLETED, event.getType()))
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/v1/responses", recordedRequest.getPath());
        assertEquals("Bearer " + API_KEY, recordedRequest.getHeader("Authorization"));
        assertNull(recordedRequest.getHeader("wind.sessionid"));
        JSONObject requestBody = JSON.parseObject(recordedRequest.getBody().readUtf8());
        assertEquals(MODEL, requestBody.getString("model"));
        assertTrue(requestBody.getBooleanValue("stream"));
        assertFalse(requestBody.getBooleanValue("store"));
        assertEquals("message", requestBody.getJSONArray("input").getJSONObject(0).getString("type"));
        assertEquals("你好", requestBody.getJSONArray("input").getJSONObject(0).getString("content"));
        assertFalse(requestBody.containsKey("provider"));
    }

    /**
     * 验证函数调用解析及 Responses 扁平工具结构。
     *
     * @throws InterruptedException 获取请求被中断
     */
    @Test
    public void testFunctionCallAndFlattenedToolDefinition() throws InterruptedException {
        JSONObject arguments = new JSONObject(true);
        arguments.put("symbol", "600519.SH");
        JSONObject item = new JSONObject(true);
        item.put("id", "fc_1");
        item.put("type", "function_call");
        item.put("call_id", "call_1");
        item.put("name", "queryFinancialData");
        item.put("arguments", arguments.toJSONString());
        JSONObject itemDone = new JSONObject(true);
        itemDone.put("response_id", "resp_2");
        itemDone.put("item", item);
        JSONObject response = new JSONObject(true);
        response.put("id", "resp_2");
        JSONObject completed = new JSONObject(true);
        completed.put("response", response);
        mockWebServer.enqueue(sseResponse(
                sseEvent("response.output_item.done", itemDone)
                        + sseEvent("response.completed", completed)));

        AgentModelRequest request = AgentModelRequest.fromMessages(
                List.of(AgentMessage.user("查询贵州茅台")),
                List.of(createToolDefinition()),
                "session-2");
        StepVerifier.create(responsesClient.respond(request))
                .assertNext(event -> {
                    assertEquals(AgentModelEvent.EventType.FUNCTION_CALL, event.getType());
                    assertEquals("call_1", event.getToolCalls().get(0).getId());
                    assertEquals(
                            arguments.toJSONString(),
                            event.getToolCalls().get(0).getFunction().getArguments());
                })
                .assertNext(event ->
                        assertEquals(AgentModelEvent.EventType.COMPLETED, event.getType()))
                .verifyComplete();

        RecordedRequest recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        JSONObject tool = JSON.parseObject(recordedRequest.getBody().readUtf8())
                .getJSONArray("tools")
                .getJSONObject(0);
        assertEquals("queryFinancialData", tool.getString("name"));
        assertTrue(tool.getBooleanValue("strict"));
        assertFalse(tool.containsKey("function"));
    }

    /**
     * 验证 HTTP 错误映射。
     */
    @Test
    public void testHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("unauthorized"));

        StepVerifier.create(responsesClient.respond(simpleRequest()))
                .expectErrorMatches(throwable ->
                        throwable instanceof DownstreamException downstreamException
                                && downstreamException.getHttpStatus() == 401)
                .verify();
    }

    /**
     * 验证流内失败事件映射。
     */
    @Test
    public void testProtocolErrorEvent() {
        JSONObject error = new JSONObject(true);
        error.put("message", "provider failed");
        JSONObject response = new JSONObject(true);
        response.put("id", "resp_error");
        response.put("error", error);
        JSONObject event = new JSONObject(true);
        event.put("response", response);
        mockWebServer.enqueue(sseResponse(sseEvent("response.failed", event)));

        StepVerifier.create(responsesClient.respond(simpleRequest()))
                .expectErrorMatches(throwable ->
                        throwable instanceof DownstreamException downstreamException
                                && downstreamException.getHttpStatus() == 502)
                .verify();
    }

    /**
     * 验证订阅取消能够终止长时间 SSE 流。
     *
     * @throws InterruptedException 获取请求被中断
     */
    @Test
    public void testCancellation() throws InterruptedException {
        JSONObject delta = new JSONObject(true);
        delta.put("response_id", "resp_cancel");
        delta.put("delta", "首段");
        String delayedBody = sseEvent("response.output_text.delta", delta)
                + " ".repeat(2048);
        mockWebServer.enqueue(sseResponse(delayedBody)
                .throttleBody(256, 100, TimeUnit.MILLISECONDS));

        StepVerifier.create(responsesClient.respond(simpleRequest()))
                .assertNext(event -> assertEquals("首段", event.getTextDelta()))
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        assertNotNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS));
    }

    private JSONObject createCompletedWithUsage() {
        JSONObject inputDetails = new JSONObject(true);
        inputDetails.put("cached_tokens", 4);
        JSONObject outputDetails = new JSONObject(true);
        outputDetails.put("reasoning_tokens", 2);
        JSONObject usage = new JSONObject(true);
        usage.put("input_tokens", 12);
        usage.put("output_tokens", 5);
        usage.put("total_tokens", 17);
        usage.put("input_tokens_details", inputDetails);
        usage.put("output_tokens_details", outputDetails);
        JSONObject response = new JSONObject(true);
        response.put("id", "resp_1");
        response.put("usage", usage);
        JSONObject event = new JSONObject(true);
        event.put("response", response);
        return event;
    }

    private AgentModelRequest simpleRequest() {
        return AgentModelRequest.fromMessages(
                List.of(AgentMessage.user("测试")),
                Collections.emptyList(),
                "session-test");
    }

    private AgentToolDefinition createToolDefinition() {
        JSONObject parameters = new JSONObject(true);
        parameters.put("type", "object");
        parameters.put("additionalProperties", false);
        return AgentToolDefinition.builder()
                .function(AgentToolDefinition.FunctionDefinition.builder()
                        .name("queryFinancialData")
                        .description("查询金融数据")
                        .parameters(parameters)
                        .strict(Boolean.TRUE)
                        .build())
                .build();
    }

    private MockResponse sseResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    private String sseEvent(String eventType, JSONObject data) {
        return "event: " + eventType + "\n"
                + "data: " + data.toJSONString() + "\n\n";
    }
}
