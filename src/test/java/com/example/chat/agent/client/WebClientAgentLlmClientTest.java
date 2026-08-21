package com.example.chat.agent.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.agent.tool.AgentToolSchemaGenerator;
import com.example.chat.agent.tool.DpuAgentTool;
import com.example.chat.common.dto.agent.tool.QueryFinancialDataInput;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.config.DownstreamProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebClientAgentLlmClient Function Calling 协议解析集成测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class WebClientAgentLlmClientTest {

    private MockWebServer mockWebServer;
    private WebClientAgentLlmClient agentLlmClient;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        DownstreamProperties.ClientProperties clientProperties =
                new DownstreamProperties.ClientProperties(
                        mockWebServer.url("/").toString(), 50, 100, Duration.ofSeconds(1), Duration.ofSeconds(5)
                );
        DownstreamProperties properties =
                new DownstreamProperties(clientProperties, null, null, null, null, null);

        agentLlmClient = new WebClientAgentLlmClient(webClient, properties);
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testStreamFinalText() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                        "data: {\"choices\": [{\"delta\": {\"content\": \"你好，\"}}]}\n\n" +
                        "data: {\"choices\": [{\"delta\": {\"content\": \"我是金融助手\"}}]}\n\n" +
                        "data: [DONE]\n\n"
                ));

        List<AgentMessage> messages = List.of(AgentMessage.user("你好"));
        Flux<AgentModelResponse> flux = agentLlmClient.chat(
                messages, Collections.emptyList(), "sess-test-1");

        StepVerifier.create(flux)
                .expectNextMatches(res -> res.getType()
                        == AgentModelResponse.ResponseType.FINAL_TEXT
                        && "你好，".equals(res.getTextDelta()))
                .expectNextMatches(res -> res.getType()
                        == AgentModelResponse.ResponseType.FINAL_TEXT
                        && "我是金融助手".equals(res.getTextDelta()))
                .verifyComplete();

        RecordedRequest req = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("/aigateway/compatible/v1/chat/completions", req.getPath());
        assertEquals("sess-test-1", req.getHeader("wind.sessionid"));
    }

    @Test
    public void testStreamToolCall() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                        "data: {\"choices\": [{\"delta\": {\"tool_calls\": [{\"index\": 0, \"id\": \"call_001\", \"type\": \"function\", \"function\": {\"name\": \"queryFinancialData\", \"arguments\": \"{\\\"query\\\":\\\"\"}}]}}]}\n\n" +
                        "data: {\"choices\": [{\"delta\": {\"tool_calls\": [{\"index\": 0, \"function\": {\"arguments\": \"茅台\\\"}\"}}]}, \"finish_reason\": \"tool_calls\"}]}\n\n"
                ));

        List<AgentMessage> messages = List.of(AgentMessage.user("查茅台"));
        AgentToolDefinition definition = new AgentToolSchemaGenerator()
                .createDefinition(DpuAgentTool.class, QueryFinancialDataInput.class);
        Flux<AgentModelResponse> flux = agentLlmClient.chat(
                messages, List.of(definition), "sess-test-2");

        StepVerifier.create(flux)
                .expectNextMatches(res -> {
                    if (res.getType() != AgentModelResponse.ResponseType.TOOL_CALL) return false;
                    if (res.getToolCalls() == null || res.getToolCalls().size() != 1) return false;
                    var tc = res.getToolCalls().get(0);
                    return "call_001".equals(tc.getId())
                            && "queryFinancialData".equals(tc.getFunction().getName())
                            && "{\"query\":\"茅台\"}".equals(tc.getFunction().getArguments());
                })
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        JSONObject requestBody = JSON.parseObject(request.getBody().readUtf8());
        assertTrue(requestBody.getBooleanValue("parallel_tool_calls"));
        JSONObject function = requestBody.getJSONArray("tools")
                .getJSONObject(0).getJSONObject("function");
        assertTrue(function.getBooleanValue("strict"));
        assertFalse(function.getJSONObject("parameters")
                .getBooleanValue("additionalProperties"));
    }


    @Test
    public void testHttpServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        Flux<AgentModelResponse> flux = agentLlmClient.chat(
                List.of(AgentMessage.user("hi")), Collections.emptyList(), "sess-test-3");

        StepVerifier.create(flux)
                .expectError(DownstreamException.class)
                .verify();
    }
}
