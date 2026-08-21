package com.example.chat.service.implement;

import com.example.chat.agent.AgentLoop;
import com.example.chat.agent.AgentTurnContext;
import com.example.chat.agent.memory.ConversationMemoryService;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.prompt.AgentPromptFactory;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.config.AgentProperties;
import com.example.chat.service.interf.ChatExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CancellationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReAct Agent 模式对话执行服务。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Service
public class AgentChatExecutionService implements ChatExecutionService {

    private final ConversationMemoryService memoryService;
    private final AgentPromptFactory promptFactory;
    private final AgentLoop agentLoop;
    private final AgentProperties agentProperties;

    public AgentChatExecutionService(
            ConversationMemoryService memoryService,
            AgentPromptFactory promptFactory,
            AgentLoop agentLoop,
            AgentProperties agentProperties) {
        this.memoryService = memoryService;
        this.promptFactory = promptFactory;
        this.agentLoop = agentLoop;
        this.agentProperties = agentProperties;
    }

    @Override
    public ExecutionMode executionMode() {
        return ExecutionMode.AGENT;
    }

    @Override
    public Flux<ChatEvent> stream(ChatRequest request) {
        if (request == null) {
            return Flux.error(new IllegalArgumentException("ChatRequest 不能为空"));
        }

        AtomicLong sequence = new AtomicLong(0);
        AtomicBoolean terminalEventEmitted = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Flux<ChatEvent> normalEvents = memoryService.getMemory(request.userId(), request.sessionId())
                .flatMapMany(memory -> createAgentEvents(request, memory, sequence, terminalEventEmitted))
                .onErrorResume(ex -> toErrorEvent(request, sequence, terminalEventEmitted, ex));

        return normalEvents
                .takeUntilOther(Mono.delay(agentProperties.totalTimeout())
                        .doOnNext(ignored -> timedOut.set(true)))
                .concatWith(Flux.defer(() -> {
                    if (!timedOut.compareAndSet(true, false)
                            || terminalEventEmitted.get()) {
                        return Flux.empty();
                    }
                    terminalEventEmitted.set(true);
                    return Flux.just(ChatEvent.error(
                            request.taskId(),
                            sequence.incrementAndGet(),
                            "AGENT_TIMEOUT",
                            "Agent 响应超时，请稍后重试"
                    ));
                }));
    }

    private Flux<ChatEvent> createAgentEvents(
            ChatRequest request,
            com.example.chat.agent.memory.ConversationMemory memory,
            AtomicLong sequence,
            AtomicBoolean terminalEventEmitted) {
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(memory)
                .messages(promptFactory.buildInitialMessages(request, memory))
                .currentTurnMessages(new ArrayList<>(
                        List.of(AgentMessage.user(request.question()))))
                .build();
        log.info("开始执行 Agent 对话，taskId={}, sessionId={}",
                request.taskId(), maskSessionId(request.sessionId()));

        Flux<ChatEvent> agentEvents = agentLoop.run(context, sequence)
                .doOnNext(event -> {
                    if (event.terminal()) {
                        terminalEventEmitted.set(true);
                    }
                });
        return agentEvents.concatWith(Flux.defer(() -> completeSuccessfulTurn(
                request, context, sequence, terminalEventEmitted)));
    }

    private Flux<ChatEvent> completeSuccessfulTurn(
            ChatRequest request,
            AgentTurnContext context,
            AtomicLong sequence,
            AtomicBoolean terminalEventEmitted) {
        if (terminalEventEmitted.get()) {
            return Flux.empty();
        }

        String fullAnswer = context.getFullAnswerBuilder().toString();
        if (fullAnswer.trim().isEmpty()) {
            terminalEventEmitted.set(true);
            return Flux.just(ChatEvent.error(
                    request.taskId(),
                    sequence.incrementAndGet(),
                    "EMPTY_MODEL_RESPONSE",
                    "Agent 未返回有效回答，请稍后重试"
            ));
        }

        context.getCurrentTurnMessages().add(AgentMessage.assistant(fullAnswer));
        return memoryService.appendTurn(request.userId(), request.sessionId(),
                        context.getCurrentTurnMessages())
                .onErrorResume(ex -> {
                    log.error("Agent 对话记忆写入异常，taskId={}", request.taskId(), ex);
                    return Mono.empty();
                })
                .thenReturn(ChatEvent.complete(request.taskId(), sequence.incrementAndGet()))
                .flux();
    }

    private Flux<ChatEvent> toErrorEvent(
            ChatRequest request,
            AtomicLong sequence,
            AtomicBoolean terminalEventEmitted,
            Throwable throwable) {
        terminalEventEmitted.set(true);
        if (isCancellation(throwable)) {
            log.info("Agent 对话已取消，taskId={}", request.taskId());
            return Flux.empty();
        }
        log.error("Agent 对话执行异常，taskId={}", request.taskId(), throwable);
        return Flux.just(ChatEvent.error(
                request.taskId(),
                sequence.incrementAndGet(),
                "AGENT_EXECUTION_ERROR",
                "Agent 执行失败，请稍后重试"
        ));
    }

    private boolean isCancellation(Throwable throwable) {
        if (throwable instanceof CancellationException) {
            return true;
        }
        return throwable instanceof DownstreamException downstreamException
                && downstreamException.getErrorType() == DownstreamException.ErrorType.CANCELLED;
    }

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 4) + "***" + sessionId.substring(sessionId.length() - 4);
    }
}
