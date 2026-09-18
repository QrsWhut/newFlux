package com.example.chat.web.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.common.exception.IdentityConfigurationException;
import com.example.chat.common.exception.InvalidOpenAiRequestException;
import com.example.chat.common.security.RequestIdentity;
import com.example.chat.common.security.RequestIdentityResolver;
import com.example.chat.common.utils.OpenAiResponseJsonFactory;
import com.example.chat.service.interf.ChatService;
import com.example.chat.web.vo.OpenAiResponseRequestVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * OpenAI Responses 兼容入站控制器。
 *
 * <p>控制器只把最后一条 user 文本交给内部 Agent，会话历史由服务端根据 conversationId 加载，避免调用方
 * 伪造历史消息污染持久化记忆。</p>
 */
@Slf4j
@RestController
public class OpenAiResponsesController {

    /** 响应标识前缀。 */
    private static final String RESPONSE_ID_PREFIX = "resp_";
    /** 输出消息标识前缀。 */
    private static final String MESSAGE_ID_PREFIX = "msg_";
    /** 无效请求错误码。 */
    private static final String INVALID_REQUEST_CODE = "invalid_request";
    /** 身份边界配置错误码。 */
    private static final String IDENTITY_CONFIGURATION_ERROR_CODE = "identity_configuration_error";
    /** Agent 执行错误码。 */
    private static final String AGENT_EXECUTION_ERROR_CODE = "agent_execution_error";
    /** Agent 取消错误码。 */
    private static final String RESPONSE_CANCELLED_CODE = "response_cancelled";
    /** Agent 缺少终态错误码。 */
    private static final String MISSING_TERMINAL_CODE = "missing_terminal_event";
    /** Agent 通用失败说明。 */
    private static final String AGENT_EXECUTION_ERROR_MESSAGE = "Agent执行失败，请稍后重试";
    /** Agent 取消说明。 */
    private static final String RESPONSE_CANCELLED_MESSAGE = "请求已取消";
    /** Agent 缺少终态说明。 */
    private static final String MISSING_TERMINAL_MESSAGE = "Agent事件流未返回终态";
    /** 禁止代理缓冲的响应头。 */
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";
    /** 禁止代理缓冲的响应头值。 */
    private static final String ACCEL_BUFFERING_DISABLED = "no";
    /** SSE 事件字段前缀。 */
    private static final String SSE_EVENT_PREFIX = "event: ";
    /** SSE 数据字段前缀。 */
    private static final String SSE_DATA_PREFIX = "data: ";
    /** SSE 字段换行符。 */
    private static final String SSE_LINE_BREAK = "\n";
    /** SSE 事件结束符。 */
    private static final String SSE_EVENT_END = "\n\n";

    /** 内部对话路由服务。 */
    private final ChatService chatService;
    /** 受信身份解析器。 */
    private final RequestIdentityResolver identityResolver;

    /**
     * 创建 OpenAI Responses 控制器。
     *
     * @param chatService 内部对话路由服务
     * @param identityResolver 受信身份解析器
     */
    public OpenAiResponsesController(
            ChatService chatService,
            RequestIdentityResolver identityResolver) {
        this.chatService = chatService;
        this.identityResolver = identityResolver;
    }

    /**
     * 创建非流式或流式 OpenAI Responses 响应。
     *
     * @param requestVO Responses 请求
     * @param request 服务端 HTTP 请求
     * @param response 服务端 HTTP 响应
     * @return 响应写入完成信号
     */
    @PostMapping("/v1/responses")
    public Mono<Void> createResponse(
            @Valid @RequestBody OpenAiResponseRequestVO requestVO,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        return Mono.defer(() -> {
            RequestIdentity identity = identityResolver.resolve(request);
            ChatRequest command = requestVO.toCommand(identity.userId());
            String model = requestVO.resolveModel();
            log.info(
                    "接收到OpenAI Responses请求, taskId={}, sessionId={}, stream={}",
                    command.taskId(),
                    maskIdentifier(command.sessionId()),
                    requestVO.streamEnabled());
            if (requestVO.streamEnabled()) {
                return writeStreamingResponse(response, command, model);
            }
            return writeNonStreamingResponse(response, command, model);
        }).onErrorResume(
                InvalidOpenAiRequestException.class,
                exception -> writeErrorResponse(
                        response,
                        HttpStatus.BAD_REQUEST,
                        INVALID_REQUEST_CODE,
                        exception.getMessage()))
                .onErrorResume(
                        IdentityConfigurationException.class,
                        exception -> writeErrorResponse(
                                response,
                                HttpStatus.SERVICE_UNAVAILABLE,
                                IDENTITY_CONFIGURATION_ERROR_CODE,
                                "服务端身份认证暂不可用"));
    }

    /**
     * 等待内部事件流终态后写入非流式完整响应。
     *
     * @param response 服务端 HTTP 响应
     * @param command 内部对话指令
     * @param model 模型标识
     * @return 响应写入完成信号
     */
    private Mono<Void> writeNonStreamingResponse(
            ServerHttpResponse response,
            ChatRequest command,
            String model) {
        long createdAtEpochSeconds = Instant.now().getEpochSecond();
        String responseId = responseId(command.taskId());
        String messageId = messageId(command.taskId());
        return chatService.stream(command)
                .materialize()
                .collect(NonStreamingState::new, NonStreamingState::accept)
                .flatMap(state -> writeAggregatedResponse(
                        response,
                        command,
                        model,
                        responseId,
                        messageId,
                        createdAtEpochSeconds,
                        state));
    }

    /**
     * 根据内部终态写入非流式成功或错误响应。
     *
     * @param response 服务端 HTTP 响应
     * @param command 内部对话指令
     * @param model 模型标识
     * @param responseId 响应标识
     * @param messageId 输出消息标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @param state 非流式聚合状态
     * @return 响应写入完成信号
     */
    private Mono<Void> writeAggregatedResponse(
            ServerHttpResponse response,
            ChatRequest command,
            String model,
            String responseId,
            String messageId,
            long createdAtEpochSeconds,
            NonStreamingState state) {
        if (state.failure) {
            return writeErrorResponse(
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    AGENT_EXECUTION_ERROR_CODE,
                    AGENT_EXECUTION_ERROR_MESSAGE);
        }
        if (state.errorCode != null) {
            return writeErrorResponse(
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    state.errorCode,
                    state.errorMessage);
        }
        if (state.cancelled) {
            return writeErrorResponse(
                    response,
                    HttpStatus.CONFLICT,
                    RESPONSE_CANCELLED_CODE,
                    RESPONSE_CANCELLED_MESSAGE);
        }
        if (!state.completed) {
            return writeErrorResponse(
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    MISSING_TERMINAL_CODE,
                    MISSING_TERMINAL_MESSAGE);
        }
        JSONObject payload = OpenAiResponseJsonFactory.completedResponse(
                responseId,
                messageId,
                command.taskId(),
                command.sessionId(),
                model,
                createdAtEpochSeconds,
                state.answer.toString());
        return writeJson(response, HttpStatus.OK, payload);
    }

    /**
     * 写入 OpenAI Responses SSE 事件流。
     *
     * @param response 服务端 HTTP 响应
     * @param command 内部对话指令
     * @param model 模型标识
     * @return 响应写入完成信号
     */
    private Mono<Void> writeStreamingResponse(
            ServerHttpResponse response,
            ChatRequest command,
            String model) {
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        response.getHeaders().setCacheControl("no-cache");
        response.getHeaders().set(ACCEL_BUFFERING_HEADER, ACCEL_BUFFERING_DISABLED);
        Publisher<DataBuffer> body = streamingPayloads(command, model)
                .map(this::encodeServerSentEvent)
                .map(content -> response.bufferFactory().wrap(
                        content.getBytes(StandardCharsets.UTF_8)));
        return response.writeWith(body);
    }

    /**
     * 将内部业务事件转换为 Responses SSE 协议事件。
     *
     * @param command 内部对话指令
     * @param model 模型标识
     * @return SSE 事件负载流
     */
    private Flux<SsePayload> streamingPayloads(ChatRequest command, String model) {
        return Flux.defer(() -> {
            long createdAtEpochSeconds = Instant.now().getEpochSecond();
            String responseId = responseId(command.taskId());
            String messageId = messageId(command.taskId());
            StreamingState state = new StreamingState();
            JSONObject createdEvent = OpenAiResponseJsonFactory.createdEvent(
                    responseId,
                    command.taskId(),
                    command.sessionId(),
                    model,
                    createdAtEpochSeconds);
            Flux<SsePayload> agentPayloads = chatService.stream(command)
                    .materialize()
                    .concatMap(signal -> mapStreamingSignal(
                            signal,
                            state,
                            command,
                            model,
                            responseId,
                            messageId,
                            createdAtEpochSeconds));
            return Flux.concat(Mono.just(toSsePayload(createdEvent)), agentPayloads);
        });
    }

    /**
     * 将一个 Reactor 信号转换为零个或一个协议事件。
     *
     * @param signal 内部事件流信号
     * @param state 流式聚合状态
     * @param command 内部对话指令
     * @param model 模型标识
     * @param responseId 响应标识
     * @param messageId 输出消息标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @return SSE 事件或空信号
     */
    private Mono<SsePayload> mapStreamingSignal(
            Signal<ChatEvent> signal,
            StreamingState state,
            ChatRequest command,
            String model,
            String responseId,
            String messageId,
            long createdAtEpochSeconds) {
        if (state.terminal) {
            return Mono.empty();
        }
        if (signal.isOnError()) {
            state.terminal = true;
            return Mono.just(toSsePayload(OpenAiResponseJsonFactory.errorEvent(
                    state.nextSequence(),
                    AGENT_EXECUTION_ERROR_CODE,
                    AGENT_EXECUTION_ERROR_MESSAGE)));
        }
        if (signal.isOnComplete()) {
            return mapMissingTerminal(state);
        }
        ChatEvent event = signal.get();
        if (event == null) {
            return Mono.empty();
        }
        return mapChatEvent(
                event,
                state,
                command,
                model,
                responseId,
                messageId,
                createdAtEpochSeconds);
    }

    /**
     * 将单个内部业务事件转换为协议事件。
     *
     * @param event 内部业务事件
     * @param state 流式聚合状态
     * @param command 内部对话指令
     * @param model 模型标识
     * @param responseId 响应标识
     * @param messageId 输出消息标识
     * @param createdAtEpochSeconds 创建秒级时间戳
     * @return SSE 事件或空信号
     */
    private Mono<SsePayload> mapChatEvent(
            ChatEvent event,
            StreamingState state,
            ChatRequest command,
            String model,
            String responseId,
            String messageId,
            long createdAtEpochSeconds) {
        if (event.type() == ChatEventType.TEXT_DELTA) {
            String text = extractText(event.payload());
            if (text.isEmpty()) {
                return Mono.empty();
            }
            state.answer.append(text);
            return Mono.just(toSsePayload(OpenAiResponseJsonFactory.textDeltaEvent(
                    messageId,
                    state.nextSequence(),
                    text)));
        }
        if (event.type() == ChatEventType.COMPLETE) {
            state.terminal = true;
            return Mono.just(toSsePayload(OpenAiResponseJsonFactory.completedEvent(
                    responseId,
                    messageId,
                    command.taskId(),
                    command.sessionId(),
                    model,
                    createdAtEpochSeconds,
                    state.nextSequence(),
                    state.answer.toString())));
        }
        if (event.type() == ChatEventType.ERROR) {
            state.terminal = true;
            ChatEvent.ErrorPayload error = extractError(event.payload());
            return Mono.just(toSsePayload(OpenAiResponseJsonFactory.errorEvent(
                    state.nextSequence(),
                    error.errorCode(),
                    error.errorMessage())));
        }
        if (event.type() == ChatEventType.CANCELLED) {
            state.terminal = true;
            return Mono.just(toSsePayload(OpenAiResponseJsonFactory.errorEvent(
                    state.nextSequence(),
                    RESPONSE_CANCELLED_CODE,
                    RESPONSE_CANCELLED_MESSAGE)));
        }
        return Mono.empty();
    }

    /**
     * 在上游未返回业务终态时发送协议错误事件。
     *
     * @param state 流式聚合状态
     * @return SSE 错误事件
     */
    private Mono<SsePayload> mapMissingTerminal(StreamingState state) {
        state.terminal = true;
        return Mono.just(toSsePayload(OpenAiResponseJsonFactory.errorEvent(
                state.nextSequence(),
                MISSING_TERMINAL_CODE,
                MISSING_TERMINAL_MESSAGE)));
    }

    /**
     * 将协议事件负载包装为 SSE 事件。
     *
     * @param payload 协议 JSON
     * @return SSE 事件负载
     */
    private SsePayload toSsePayload(JSONObject payload) {
        return new SsePayload(payload.getString("type"), payload);
    }

    /**
     * 将 SSE 事件编码为 UTF-8 文本帧。
     *
     * @param payload SSE 事件负载
     * @return SSE 文本帧
     */
    private String encodeServerSentEvent(SsePayload payload) {
        return SSE_EVENT_PREFIX + payload.eventName()
                + SSE_LINE_BREAK
                + SSE_DATA_PREFIX + JSON.toJSONString(payload.data())
                + SSE_EVENT_END;
    }

    /**
     * 写入 OpenAI JSON 错误响应。
     *
     * @param response 服务端 HTTP 响应
     * @param status HTTP 状态
     * @param errorCode 错误码
     * @param errorMessage 错误说明
     * @return 响应写入完成信号
     */
    private Mono<Void> writeErrorResponse(
            ServerHttpResponse response,
            HttpStatus status,
            String errorCode,
            String errorMessage) {
        return writeJson(
                response,
                status,
                OpenAiResponseJsonFactory.errorEnvelope(errorCode, errorMessage));
    }

    /**
     * 使用 FastJSON 序列化并写入 JSON 响应。
     *
     * @param response 服务端 HTTP 响应
     * @param status HTTP 状态
     * @param payload JSON 负载
     * @return 响应写入完成信号
     */
    private Mono<Void> writeJson(
            ServerHttpResponse response,
            HttpStatus status,
            JSONObject payload) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        byte[] body = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * 从内部事件负载提取回答文本，忽略推理文本。
     *
     * @param payload 内部事件负载
     * @return 回答文本
     */
    private String extractText(Object payload) {
        if (payload instanceof ChatEvent.TextDelta textDelta) {
            return textDelta.content() == null ? "" : textDelta.content();
        }
        return payload instanceof String text ? text : "";
    }

    /**
     * 从内部事件负载提取结构化错误。
     *
     * @param payload 内部事件负载
     * @return 结构化错误
     */
    private ChatEvent.ErrorPayload extractError(Object payload) {
        if (payload instanceof ChatEvent.ErrorPayload errorPayload) {
            return errorPayload;
        }
        return new ChatEvent.ErrorPayload(
                AGENT_EXECUTION_ERROR_CODE,
                AGENT_EXECUTION_ERROR_MESSAGE);
    }

    /**
     * 构建 Responses 响应标识。
     *
     * @param taskId 任务标识
     * @return 响应标识
     */
    private String responseId(String taskId) {
        return RESPONSE_ID_PREFIX + taskId;
    }

    /**
     * 构建 Responses 输出消息标识。
     *
     * @param taskId 任务标识
     * @return 输出消息标识
     */
    private String messageId(String taskId) {
        return MESSAGE_ID_PREFIX + taskId;
    }

    /**
     * 对日志中的会话标识进行脱敏。
     *
     * @param identifier 会话标识
     * @return 脱敏标识
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null || identifier.length() <= 8) {
            return "***";
        }
        return identifier.substring(0, 6)
                + "***"
                + identifier.substring(identifier.length() - 2);
    }

    /**
     * 单个 SSE 协议事件。
     *
     * @param eventName SSE 事件名称
     * @param data 事件 JSON 数据
     */
    private record SsePayload(String eventName, JSONObject data) {
    }

    /**
     * 流式响应的单订阅聚合状态。
     */
    private static final class StreamingState {

        /** 完整回答缓冲区。 */
        private final StringBuilder answer = new StringBuilder();
        /** 当前协议序号。 */
        private long sequenceNumber;
        /** 是否已经输出终态。 */
        private boolean terminal;

        /**
         * 获取下一个连续协议序号。
         *
         * @return 下一个协议序号
         */
        private long nextSequence() {
            sequenceNumber += 1L;
            return sequenceNumber;
        }
    }

    /**
     * 非流式响应的单订阅聚合状态。
     */
    private static final class NonStreamingState {

        /** 完整回答缓冲区。 */
        private final StringBuilder answer = new StringBuilder();
        /** 业务错误码。 */
        private String errorCode;
        /** 业务错误说明。 */
        private String errorMessage;
        /** 是否收到取消终态。 */
        private boolean cancelled;
        /** 是否收到成功终态。 */
        private boolean completed;
        /** 是否收到 Reactor 异常信号。 */
        private boolean failure;

        /**
         * 聚合一个 Reactor 信号。
         *
         * @param signal 内部事件流信号
         */
        private void accept(Signal<ChatEvent> signal) {
            if (signal.isOnError()) {
                failure = true;
                return;
            }
            if (!signal.isOnNext()) {
                return;
            }
            ChatEvent event = signal.get();
            if (event == null) {
                return;
            }
            if (event.type() == ChatEventType.TEXT_DELTA) {
                appendText(event.payload());
            } else if (event.type() == ChatEventType.COMPLETE) {
                completed = true;
            } else if (event.type() == ChatEventType.CANCELLED) {
                cancelled = true;
            } else if (event.type() == ChatEventType.ERROR) {
                acceptError(event.payload());
            }
        }

        /**
         * 追加回答文本。
         *
         * @param payload 内部事件负载
         */
        private void appendText(Object payload) {
            if (payload instanceof ChatEvent.TextDelta textDelta
                    && textDelta.content() != null) {
                answer.append(textDelta.content());
            } else if (payload instanceof String text) {
                answer.append(text);
            }
        }

        /**
         * 保存结构化错误负载。
         *
         * @param payload 内部事件负载
         */
        private void acceptError(Object payload) {
            if (payload instanceof ChatEvent.ErrorPayload errorPayload) {
                errorCode = errorPayload.errorCode();
                errorMessage = errorPayload.errorMessage();
                return;
            }
            errorCode = AGENT_EXECUTION_ERROR_CODE;
            errorMessage = AGENT_EXECUTION_ERROR_MESSAGE;
        }
    }
}
