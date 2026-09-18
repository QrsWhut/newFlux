package com.example.chat.agent.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelInputItem;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.agent.model.AgentModelUsage;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.config.AiRoutingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * OpenAI Responses API 流式客户端。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Slf4j
public class OpenAiResponsesClient implements AgentLlmClient {

    /** Responses API 默认路径。 */
    public static final String DEFAULT_RESPONSES_PATH = "/v1/responses";

    private static final String TYPE_FUNCTION = "function";
    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    private static final String EVENT_OUTPUT_ITEM_DONE = "response.output_item.done";
    private static final String EVENT_FUNCTION_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    private static final String EVENT_FUNCTION_ARGUMENTS_DONE = "response.function_call_arguments.done";
    private static final String EVENT_COMPLETED = "response.completed";
    private static final String EVENT_FAILED = "response.failed";
    private static final String EVENT_INCOMPLETE = "response.incomplete";
    private static final String EVENT_ERROR = "error";
    private static final String DONE_MARKER = "[DONE]";
    private static final String GENERATED_CALL_ID_PREFIX = "call_";
    private static final int HTTP_OK_STATUS = 200;
    private static final int UPSTREAM_PROTOCOL_ERROR_STATUS = 502;
    private static final int RESPONSE_TIMEOUT_STATUS = 504;
    private static final int MAX_ERROR_BODY_LENGTH = 256;
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    private final String providerId;
    private final WebClient webClient;
    private final String apiKey;
    private final String defaultModel;
    private final String responsesPath;
    private final Duration responseTimeout;

    /**
     * 创建供应商客户端。
     *
     * @param providerId 供应商标识
     * @param webClient 供应商专属 WebClient
     * @param providerProperties 供应商配置
     */
    public OpenAiResponsesClient(
            String providerId,
            WebClient webClient,
            AiRoutingProperties.ProviderProperties providerProperties) {
        if (!StringUtils.hasText(providerId)) {
            throw new IllegalArgumentException("模型供应商标识不能为空");
        }
        if (webClient == null) {
            throw new IllegalArgumentException("模型供应商 WebClient 不能为空");
        }
        if (providerProperties == null) {
            throw new IllegalArgumentException("模型供应商配置不能为空");
        }
        this.providerId = providerId;
        this.webClient = webClient;
        this.apiKey = providerProperties.getApiKey();
        this.defaultModel = providerProperties.getModel();
        this.responsesPath = StringUtils.hasText(providerProperties.getResponsesPath())
                ? providerProperties.getResponsesPath()
                : DEFAULT_RESPONSES_PATH;
        this.responseTimeout = providerProperties.getResponseTimeout() == null
                ? DEFAULT_RESPONSE_TIMEOUT
                : providerProperties.getResponseTimeout();
    }

    /**
     * 获取供应商标识。
     *
     * @return 供应商标识
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * 获取供应商默认模型。
     *
     * @return 默认模型标识
     */
    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * 调用 OpenAI Responses API。
     *
     * @param request 模型请求
     * @return 类型化模型事件流
     */
    @Override
    public Flux<AgentModelEvent> respond(AgentModelRequest request) {
        if (request == null) {
            return Flux.error(new IllegalArgumentException("模型请求不能为空"));
        }
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : defaultModel;
        if (!StringUtils.hasText(model)) {
            return Flux.error(new IllegalStateException("模型标识不能为空"));
        }
        AgentModelRequest normalizedRequest = request.toBuilder()
                .provider(providerId)
                .model(model)
                .stream(Boolean.TRUE)
                .build();
        return Flux.defer(() -> executeRequest(normalizedRequest));
    }

    private Flux<AgentModelEvent> executeRequest(AgentModelRequest request) {
        ResponsesSseTransformer transformer = new ResponsesSseTransformer(providerId, request.getModel());
        ParameterizedTypeReference<ServerSentEvent<String>> typeReference = new ParameterizedTypeReference<>() {
        };
        return webClient.post()
                .uri(responsesPath)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    if (StringUtils.hasText(apiKey)) {
                        headers.setBearerAuth(apiKey);
                    }

                })
                .bodyValue(createPayload(request).toJSONString())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toHttpError)
                .bodyToFlux(typeReference)
                .timeout(responseTimeout)
                .concatMap(transformer::transform)
                .concatWith(Flux.defer(transformer::finish))
                .onErrorMap(TimeoutException.class, exception -> new DownstreamException(
                        downstreamName(),
                        RESPONSE_TIMEOUT_STATUS,
                        DownstreamException.ErrorType.RESPONSE_TIMEOUT,
                        true,
                        false,
                        "模型流式响应超时",
                        exception))
                .onErrorMap(WebClientRequestException.class, exception -> new DownstreamException(
                        downstreamName(),
                        0,
                        DownstreamException.ErrorType.UNKNOWN,
                        true,
                        false,
                        "模型供应商连接失败",
                        exception))
                .doOnCancel(() -> log.info("模型流式调用已取消，provider={}", providerId));
    }

    private JSONObject createPayload(AgentModelRequest request) {
        JSONObject payload = new JSONObject(true);
        payload.put("model", request.getModel());
        payload.put("stream", true);
        payload.put("store", false);
        JSONArray input = request.getInput() == null
                ? new JSONArray()
                : JSON.parseArray(JSON.toJSONString(request.getInput()));
        payload.put("input", input);
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            JSONArray tools = new JSONArray(request.getTools().size());
            for (AgentToolDefinition toolDefinition : request.getTools()) {
                if (toolDefinition != null) {
                    tools.add(toolDefinition.toResponsesTool());
                }
            }
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
            payload.put("parallel_tool_calls", true);
        }
        if (request.getMaxOutputTokens() != null) {
            payload.put("max_output_tokens", request.getMaxOutputTokens());
        }
        return payload;
    }

    private Mono<? extends Throwable> toHttpError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("无响应体")
                .map(errorBody -> {
                    int statusCode = response.statusCode().value();
                    DownstreamException.ErrorType errorType = response.statusCode().is4xxClientError()
                            ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR
                            : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    log.error("模型供应商接口异常，provider={}, statusCode={}", providerId, statusCode);
                    return new DownstreamException(
                            downstreamName(),
                            statusCode,
                            errorType,
                            response.statusCode().is5xxServerError(),
                            false,
                            "模型供应商响应异常：" + truncate(errorBody));
                });
    }

    private String downstreamName() {
        return "AI模型供应商-" + providerId;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    /**
     * 将 Responses SSE 事件转换为内部类型化事件。
     */
    private static final class ResponsesSseTransformer {

        private final String providerId;
        private final String modelName;
        private final Map<String, FunctionCallBuffer> functionCallBuffers = new LinkedHashMap<>();
        private final Set<String> emittedCallIds = new HashSet<>();
        private String responseId;

        private ResponsesSseTransformer(String providerId, String modelName) {
            this.providerId = providerId;
            this.modelName = modelName;
        }

        private Flux<AgentModelEvent> transform(ServerSentEvent<String> event) {
            String data = event.data();
            if (!StringUtils.hasText(data)) {
                return Flux.empty();
            }
            if (DONE_MARKER.equals(data.trim())) {
                return finish();
            }
            try {
                JSONObject json = JSON.parseObject(data);
                updateResponseId(json);
                String eventType = StringUtils.hasText(json.getString("type"))
                        ? json.getString("type")
                        : event.event();
                return transformEvent(eventType, json);
            } catch (JSONException exception) {
                return Flux.error(parseError("解析模型 SSE 数据失败", exception));
            }
        }

        private Flux<AgentModelEvent> transformEvent(String eventType, JSONObject json) {
            if (!StringUtils.hasText(eventType)) {
                return Flux.empty();
            }
            return switch (eventType) {
                case EVENT_OUTPUT_TEXT_DELTA -> transformTextDelta(json);
                case EVENT_OUTPUT_ITEM_ADDED -> bufferOutputItem(json.getJSONObject("item"), false);
                case EVENT_OUTPUT_ITEM_DONE -> bufferOutputItem(json.getJSONObject("item"), true);
                case EVENT_FUNCTION_ARGUMENTS_DELTA -> bufferArgumentsDelta(json);
                case EVENT_FUNCTION_ARGUMENTS_DONE -> completeArguments(json);
                case EVENT_COMPLETED -> completeResponse(json);
                case EVENT_FAILED, EVENT_INCOMPLETE, EVENT_ERROR ->
                        Flux.error(protocolError(extractErrorMessage(json)));
                default -> Flux.empty();
            };
        }

        private Flux<AgentModelEvent> transformTextDelta(JSONObject json) {
            String delta = json.getString("delta");
            if (!StringUtils.hasLength(delta)) {
                return Flux.empty();
            }
            return Flux.just(AgentModelEvent.textDelta(responseId, delta));
        }

        private Flux<AgentModelEvent> bufferOutputItem(JSONObject item, boolean completed) {
            if (item == null || !AgentModelInputItem.TYPE_FUNCTION_CALL.equals(item.getString("type"))) {
                return Flux.empty();
            }
            String callId = item.getString("call_id");
            if (StringUtils.hasText(callId) && emittedCallIds.contains(callId)) {
                return Flux.empty();
            }
            FunctionCallBuffer buffer = getOrCreateBuffer(item.getString("id"), callId);
            updateBuffer(buffer, item);
            if (!completed) {
                return Flux.empty();
            }
            return emitBuffer(buffer);
        }

        private Flux<AgentModelEvent> bufferArgumentsDelta(JSONObject json) {
            FunctionCallBuffer buffer = findOrCreateBuffer(json);
            String delta = json.getString("delta");
            if (delta != null) {
                buffer.arguments.append(delta);
            }
            return Flux.empty();
        }

        private Flux<AgentModelEvent> completeArguments(JSONObject json) {
            FunctionCallBuffer buffer = findOrCreateBuffer(json);
            String arguments = json.getString("arguments");
            if (arguments != null) {
                buffer.arguments.setLength(0);
                buffer.arguments.append(arguments);
            }
            return emitBuffer(buffer);
        }

        private Flux<AgentModelEvent> completeResponse(JSONObject json) {
            JSONObject response = json.getJSONObject("response");
            if (response != null) {
                updateResponseIdFromResponse(response);
                bufferResponseOutput(response);
            }
            List<AgentModelEvent> events = new ArrayList<>();
            events.addAll(drainFunctionCalls());
            AgentModelUsage usage = parseUsage(response);
            if (usage != null) {
                events.add(AgentModelEvent.usage(responseId, providerId, modelName, usage));
            }
            events.add(AgentModelEvent.completed(responseId, providerId, modelName));
            return Flux.fromIterable(events);
        }

        private void bufferResponseOutput(JSONObject response) {
            JSONArray output = response.getJSONArray("output");
            if (output == null || output.isEmpty()) {
                return;
            }
            for (int index = 0; index < output.size(); index++) {
                JSONObject item = output.getJSONObject(index);
                if (item == null || !AgentModelInputItem.TYPE_FUNCTION_CALL.equals(item.getString("type"))) {
                    continue;
                }
                String callId = item.getString("call_id");
                if (StringUtils.hasText(callId) && emittedCallIds.contains(callId)) {
                    continue;
                }
                FunctionCallBuffer buffer = getOrCreateBuffer(item.getString("id"), callId);
                updateBuffer(buffer, item);
            }
        }

        private AgentModelUsage parseUsage(JSONObject response) {
            if (response == null) {
                return null;
            }
            JSONObject usage = response.getJSONObject("usage");
            if (usage == null) {
                return null;
            }
            JSONObject inputDetails = usage.getJSONObject("input_tokens_details");
            JSONObject outputDetails = usage.getJSONObject("output_tokens_details");
            return AgentModelUsage.builder()
                    .inputTokens(usage.getLong("input_tokens"))
                    .outputTokens(usage.getLong("output_tokens"))
                    .totalTokens(usage.getLong("total_tokens"))
                    .cachedInputTokens(inputDetails == null ? null : inputDetails.getLong("cached_tokens"))
                    .reasoningTokens(outputDetails == null ? null : outputDetails.getLong("reasoning_tokens"))
                    .build();
        }

        private FunctionCallBuffer findOrCreateBuffer(JSONObject json) {
            String itemId = json.getString("item_id");
            String callId = json.getString("call_id");
            if (StringUtils.hasText(itemId) && functionCallBuffers.containsKey(itemId)) {
                return functionCallBuffers.get(itemId);
            }
            if (StringUtils.hasText(callId)) {
                for (FunctionCallBuffer buffer : functionCallBuffers.values()) {
                    if (callId.equals(buffer.callId)) {
                        return buffer;
                    }
                }
            }
            return getOrCreateBuffer(itemId, callId);
        }

        private FunctionCallBuffer getOrCreateBuffer(String itemId, String callId) {
            String key = StringUtils.hasText(itemId)
                    ? itemId
                    : StringUtils.hasText(callId) ? callId : UUID.randomUUID().toString();
            FunctionCallBuffer buffer = functionCallBuffers.computeIfAbsent(
                    key, ignored -> new FunctionCallBuffer(key));
            if (StringUtils.hasText(callId)) {
                buffer.callId = callId;
            }
            return buffer;
        }

        private void updateBuffer(FunctionCallBuffer buffer, JSONObject item) {
            String callId = item.getString("call_id");
            if (StringUtils.hasText(callId)) {
                buffer.callId = callId;
            }
            String name = item.getString("name");
            if (StringUtils.hasText(name)) {
                buffer.name = name;
            }
            String arguments = item.getString("arguments");
            if (arguments != null && buffer.arguments.isEmpty()) {
                buffer.arguments.append(arguments);
            }
        }

        private Flux<AgentModelEvent> emitBuffer(FunctionCallBuffer buffer) {
            functionCallBuffers.remove(buffer.key);
            AgentToolCall toolCall = buildToolCall(buffer);
            emittedCallIds.add(toolCall.getId());
            return Flux.just(AgentModelEvent.functionCalls(responseId, List.of(toolCall)));
        }

        private List<AgentModelEvent> drainFunctionCalls() {
            List<AgentModelEvent> events = new ArrayList<>(functionCallBuffers.size());
            List<FunctionCallBuffer> buffers = new ArrayList<>(functionCallBuffers.values());
            for (FunctionCallBuffer buffer : buffers) {
                AgentToolCall toolCall = buildToolCall(buffer);
                if (emittedCallIds.add(toolCall.getId())) {
                    events.add(AgentModelEvent.functionCalls(responseId, List.of(toolCall)));
                }
            }
            functionCallBuffers.clear();
            return events;
        }

        private AgentToolCall buildToolCall(FunctionCallBuffer buffer) {
            String callId = StringUtils.hasText(buffer.callId)
                    ? buffer.callId
                    : GENERATED_CALL_ID_PREFIX + UUID.randomUUID();
            AgentToolCall.FunctionCall functionCall = AgentToolCall.FunctionCall.builder()
                    .name(buffer.name)
                    .arguments(buffer.arguments.toString())
                    .build();
            return AgentToolCall.builder()
                    .id(callId)
                    .type(TYPE_FUNCTION)
                    .function(functionCall)
                    .build();
        }

        private Flux<AgentModelEvent> finish() {
            return Flux.fromIterable(drainFunctionCalls());
        }

        private void updateResponseId(JSONObject json) {
            String eventResponseId = json.getString("response_id");
            if (StringUtils.hasText(eventResponseId)) {
                responseId = eventResponseId;
            }
            JSONObject response = json.getJSONObject("response");
            if (response != null) {
                updateResponseIdFromResponse(response);
            }
        }

        private void updateResponseIdFromResponse(JSONObject response) {
            String currentResponseId = response.getString("id");
            if (StringUtils.hasText(currentResponseId)) {
                responseId = currentResponseId;
            }
        }

        private String extractErrorMessage(JSONObject json) {
            JSONObject error = json.getJSONObject("error");
            if (error != null && StringUtils.hasText(error.getString("message"))) {
                return error.getString("message");
            }
            JSONObject response = json.getJSONObject("response");
            if (response != null) {
                JSONObject responseError = response.getJSONObject("error");
                if (responseError != null && StringUtils.hasText(responseError.getString("message"))) {
                    return responseError.getString("message");
                }
            }
            if (StringUtils.hasText(json.getString("message"))) {
                return json.getString("message");
            }
            return "模型供应商返回失败事件";
        }

        private DownstreamException protocolError(String message) {
            return new DownstreamException(
                    "AI模型供应商-" + providerId,
                    UPSTREAM_PROTOCOL_ERROR_STATUS,
                    DownstreamException.ErrorType.HTTP_SERVER_ERROR,
                    false,
                    false,
                    truncate(message));
        }

        private DownstreamException parseError(String message, JSONException exception) {
            return new DownstreamException(
                    "AI模型供应商-" + providerId,
                    HTTP_OK_STATUS,
                    DownstreamException.ErrorType.PARSE_ERROR,
                    false,
                    false,
                    message,
                    exception);
        }
    }

    /**
     * 尚未完成的函数调用缓冲区。
     */
    private static final class FunctionCallBuffer {

        private final String key;
        private String callId;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private FunctionCallBuffer(String key) {
            this.key = key;
        }
    }
}
