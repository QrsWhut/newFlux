package com.example.chat.web.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.security.RequestIdentity;
import com.example.chat.common.security.RequestIdentityResolver;
import com.example.chat.service.interf.ChatService;
import com.example.chat.web.vo.OpenAiResponseRequestVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Responses 入站控制器测试。
 */
class OpenAiResponsesControllerTest {

    /** 受信测试用户。 */
    private static final String TRUSTED_USER_ID = "trusted-user";

    /**
     * 验证非流式响应只在内部成功终态后写出完整 JSON。
     */
    @Test
    void shouldWriteNonStreamingResponseAfterTerminalEvent() {
        Sinks.Many<ChatEvent> eventSink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
        ChatService chatService = request -> {
            capturedRequest.set(request);
            return eventSink.asFlux();
        };
        OpenAiResponsesController controller = controller(chatService);
        OpenAiResponseRequestVO requestVO = request(false);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(controller.createResponse(
                        requestVO,
                        exchange.getRequest(),
                        exchange.getResponse()))
                .then(() -> eventSink.tryEmitNext(ChatEvent.text(
                        "task-001", 1L, "第一段", "")))
                .then(() -> assertNull(exchange.getResponse().getStatusCode()))
                .then(() -> eventSink.tryEmitNext(ChatEvent.text(
                        "task-001", 2L, "第二段", "")))
                .then(() -> eventSink.tryEmitNext(ChatEvent.complete("task-001", 3L)))
                .then(eventSink::tryEmitComplete)
                .verifyComplete();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        JSONObject response = JSON.parseObject(exchange.getResponse().getBodyAsString().block());
        assertEquals("response", response.getString("object"));
        assertEquals("completed", response.getString("status"));
        assertEquals("conversation-001", response.getJSONObject("metadata").getString("conversationId"));
        JSONArray content = response.getJSONArray("output").getJSONObject(0).getJSONArray("content");
        assertEquals("第一段第二段", content.getJSONObject(0).getString("text"));
        assertEquals(TRUSTED_USER_ID, capturedRequest.get().userId());
        assertTrue(capturedRequest.get().history().isEmpty());
    }

    /**
     * 验证流式响应包含 created、delta 与 completed 事件。
     */
    @Test
    void shouldWriteOpenAiStreamingEvents() {
        ChatService chatService = request -> Flux.just(
                ChatEvent.text(request.taskId(), 1L, "流式回答", ""),
                ChatEvent.complete(request.taskId(), 2L));
        OpenAiResponsesController controller = controller(chatService);
        MockServerWebExchange exchange = exchange();

        controller.createResponse(
                        request(true),
                        exchange.getRequest(),
                        exchange.getResponse())
                .block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.TEXT_EVENT_STREAM, exchange.getResponse().getHeaders().getContentType());
        assertTrue(body.contains("event: response.created"));
        assertTrue(body.contains("event: response.output_text.delta"));
        assertTrue(body.contains("\"delta\":\"流式回答\""));
        assertTrue(body.contains("event: response.completed"));
        assertFalse(body.contains("event: error"));
    }

    /**
     * 验证内部异常被转换为流式 error 终态且不会泄露异常详情。
     */
    @Test
    void shouldWriteStreamingErrorEvent() {
        ChatService chatService = request -> Flux.error(
                new IllegalStateException("sensitive-upstream-detail"));
        OpenAiResponsesController controller = controller(chatService);
        MockServerWebExchange exchange = exchange();

        controller.createResponse(
                        request(true),
                        exchange.getRequest(),
                        exchange.getResponse())
                .block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body.contains("event: response.created"));
        assertTrue(body.contains("event: error"));
        assertTrue(body.contains("agent_execution_error"));
        assertFalse(body.contains("sensitive-upstream-detail"));
    }

    /**
     * 验证身份边界拒绝请求时返回 OpenAI 错误信封。
     */
    @Test
    void shouldWriteInvalidIdentityErrorEnvelope() {
        RequestIdentityResolver rejectingResolver = request -> {
            throw new com.example.chat.common.exception.InvalidOpenAiRequestException(
                    "请求身份签名无效");
        };
        OpenAiResponsesController controller = new OpenAiResponsesController(
                request -> Flux.empty(),
                rejectingResolver);
        MockServerWebExchange exchange = exchange();

        controller.createResponse(
                        request(false),
                        exchange.getRequest(),
                        exchange.getResponse())
                .block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
        JSONObject envelope = JSON.parseObject(exchange.getResponse().getBodyAsString().block());
        assertEquals("invalid_request", envelope.getJSONObject("error").getString("code"));
    }

    /**
     * 创建使用固定身份解析器的控制器。
     *
     * @param chatService 内部对话服务
     * @return 测试控制器
     */
    private OpenAiResponsesController controller(ChatService chatService) {
        RequestIdentityResolver resolver = request -> new RequestIdentity(TRUSTED_USER_ID);
        return new OpenAiResponsesController(chatService, resolver);
    }

    /**
     * 创建标准 Responses 测试请求。
     *
     * @param stream 是否流式返回
     * @return 测试请求
     */
    private OpenAiResponseRequestVO request(boolean stream) {
        OpenAiResponseRequestVO requestVO = new OpenAiResponseRequestVO();
        requestVO.setModel("gpt-5.6-sol");
        requestVO.setInput("测试问题");
        requestVO.setStream(stream);
        requestVO.setMetadata(Map.of(
                "conversationId", "conversation-001",
                "taskId", "task-001"));
        return requestVO;
    }

    /**
     * 创建模拟 WebFlux 请求交换对象。
     *
     * @return 模拟请求交换对象
     */
    private MockServerWebExchange exchange() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/v1/responses")
                .build();
        return MockServerWebExchange.from(request);
    }
}
