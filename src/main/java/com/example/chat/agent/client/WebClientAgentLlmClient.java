package com.example.chat.agent.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.config.DownstreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * OpenAI 兼容 Function Calling 网关的 Agent LLM 客户端。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
public class WebClientAgentLlmClient implements AgentLlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/aigateway/compatible/v1/chat/completions";

    private final WebClient webClient;
    private final DownstreamProperties downstreamProperties;

    public WebClientAgentLlmClient(WebClient webClient, DownstreamProperties downstreamProperties) {
        this.webClient = webClient;
        this.downstreamProperties = downstreamProperties;
    }

    @Override
    public Flux<AgentModelResponse> chat(
            List<AgentMessage> messages,
            List<AgentToolDefinition> tools,
            String sessionId) {
        JSONObject payload = new JSONObject();
        payload.put("stream", true);
        payload.put("messages", JSON.parseArray(JSON.toJSONString(messages)));
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", JSON.parseArray(JSON.toJSONString(tools)));
            payload.put("tool_choice", "auto");
        }

        ParameterizedTypeReference<ServerSentEvent<String>> typeReference = new ParameterizedTypeReference<>() {
        };
        Duration responseTimeout = downstreamProperties.llm().responseTimeout();
        String maskedSessionId = maskSessionId(sessionId);

        return webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(sessionId)) {
                        headers.set("wind.sessionid", sessionId);
                    }
                })
                .bodyValue(payload.toJSONString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toHttpError(response, maskedSessionId))
                .bodyToFlux(typeReference)
                .timeout(responseTimeout)
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex ->
                        new DownstreamException(
                                "AgentLLM",
                                504,
                                DownstreamException.ErrorType.RESPONSE_TIMEOUT,
                                false,
                                false,
                                "Agent LLM 调用超时",
                                ex
                        ))
                .flatMap(new SseResponseTransformer());
    }

    private Mono<? extends Throwable> toHttpError(
            org.springframework.web.reactive.function.client.ClientResponse response,
            String maskedSessionId) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("无响应体")
                .flatMap(errorBody -> {
                    int statusCode = response.statusCode().value();
                    DownstreamException.ErrorType errorType = response.statusCode().is4xxClientError()
                            ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR
                            : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    log.error("Agent LLM 接口异常，statusCode={}, sessionId={}", statusCode, maskedSessionId);
                    return Mono.error(new DownstreamException(
                            "AgentLLM",
                            statusCode,
                            errorType,
                            false,
                            false,
                            "Agent LLM 响应异常：" + truncateErrorBody(errorBody)
                    ));
                });
    }

    private static class SseResponseTransformer
            implements Function<ServerSentEvent<String>, Flux<AgentModelResponse>> {

        private final Map<Integer, ToolCallBuffer> toolCallBuffers = new HashMap<>();

        @Override
        public Flux<AgentModelResponse> apply(ServerSentEvent<String> event) {
            String data = event.data();
            if (!StringUtils.hasText(data) || "[DONE]".equals(data.trim())) {
                return emitBufferedToolCalls();
            }

            try {
                return parseEvent(data);
            } catch (com.alibaba.fastjson.JSONException ex) {
                return Flux.error(new DownstreamException(
                        "AgentLLM",
                        200,
                        DownstreamException.ErrorType.PARSE_ERROR,
                        false,
                        false,
                        "解析 Agent LLM SSE 数据失败",
                        ex
                ));
            }
        }

        private Flux<AgentModelResponse> parseEvent(String data) {
            JSONObject json = JSON.parseObject(data);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return Flux.empty();
            }

            JSONObject choice = choices.getJSONObject(0);
            JSONObject delta = choice.getJSONObject("delta");
            List<AgentModelResponse> responses = new ArrayList<>();
            if (delta != null) {
                appendTextDelta(delta, responses);
                bufferToolCalls(delta);
            }
            String finishReason = choice.getString("finish_reason");
            if (("tool_calls".equalsIgnoreCase(finishReason) || "stop".equalsIgnoreCase(finishReason))
                    && !toolCallBuffers.isEmpty()) {
                responses.add(AgentModelResponse.toolCalls(buildToolCalls()));
                toolCallBuffers.clear();
            }
            return Flux.fromIterable(responses);
        }

        private void appendTextDelta(JSONObject delta, List<AgentModelResponse> responses) {
            String content = delta.getString("content");
            if (StringUtils.hasText(content)) {
                responses.add(AgentModelResponse.textDelta(content));
            }
        }

        private void bufferToolCalls(JSONObject delta) {
            JSONArray toolCalls = delta.getJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                return;
            }
            for (int index = 0; index < toolCalls.size(); index++) {
                JSONObject toolCall = toolCalls.getJSONObject(index);
                int toolIndex = toolCall.getIntValue("index");
                ToolCallBuffer buffer = toolCallBuffers.computeIfAbsent(toolIndex, ignored -> new ToolCallBuffer());
                buffer.id = toolCall.getString("id") == null ? buffer.id : toolCall.getString("id");
                buffer.type = toolCall.getString("type") == null ? buffer.type : toolCall.getString("type");
                JSONObject function = toolCall.getJSONObject("function");
                if (function != null) {
                    buffer.name = function.getString("name") == null ? buffer.name : function.getString("name");
                    String arguments = function.getString("arguments");
                    if (arguments != null) {
                        buffer.arguments.append(arguments);
                    }
                }
            }
        }

        private Flux<AgentModelResponse> emitBufferedToolCalls() {
            if (toolCallBuffers.isEmpty()) {
                return Flux.empty();
            }
            List<AgentToolCall> toolCalls = buildToolCalls();
            toolCallBuffers.clear();
            return Flux.just(AgentModelResponse.toolCalls(toolCalls));
        }

        private List<AgentToolCall> buildToolCalls() {
            List<Integer> indexes = new ArrayList<>(toolCallBuffers.keySet());
            Collections.sort(indexes);
            List<AgentToolCall> toolCalls = new ArrayList<>();
            for (Integer index : indexes) {
                ToolCallBuffer buffer = toolCallBuffers.get(index);
                AgentToolCall.FunctionCall functionCall = AgentToolCall.FunctionCall.builder()
                        .name(buffer.name)
                        .arguments(buffer.arguments.toString())
                        .build();
                toolCalls.add(AgentToolCall.builder()
                        .id(buffer.id == null ? "call_" + UUID.randomUUID() : buffer.id)
                        .type(buffer.type == null ? "function" : buffer.type)
                        .function(functionCall)
                        .build());
            }
            return toolCalls;
        }

        private static class ToolCallBuffer {
            private String id;
            private String type;
            private String name;
            private final StringBuilder arguments = new StringBuilder();
        }
    }

    private static String maskSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId) || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 4) + "***" + sessionId.substring(sessionId.length() - 4);
    }

    private static String truncateErrorBody(String errorBody) {
        int maxLength = 256;
        if (errorBody.length() <= maxLength) {
            return errorBody;
        }
        return errorBody.substring(0, maxLength) + "...";
    }
}