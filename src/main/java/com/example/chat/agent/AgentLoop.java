package com.example.chat.agent;

import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.tool.AgentTool;
import com.example.chat.agent.tool.AgentToolRegistry;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 响应式 ReAct 循环执行器。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class AgentLoop {

    private final AgentLlmClient agentLlmClient;
    private final AgentToolRegistry toolRegistry;
    private final AgentProperties agentProperties;

    public AgentLoop(
            AgentLlmClient agentLlmClient,
            AgentToolRegistry toolRegistry,
            AgentProperties agentProperties) {
        this.agentLlmClient = agentLlmClient;
        this.toolRegistry = toolRegistry;
        this.agentProperties = agentProperties;
    }

    /**
     * 启动单轮 ReAct 循环并产生 SSE 事件流。
     *
     * @param context 单轮上下文
     * @param sequence 事件序号生成器
     * @return 业务事件流
     */
    public Flux<ChatEvent> run(AgentTurnContext context, AtomicLong sequence) {
        return executeStep(context, sequence);
    }

    private Flux<ChatEvent> executeStep(AgentTurnContext context, AtomicLong sequence) {
        if (context.getStepCount() >= agentProperties.maxTurns()) {
            log.warn("Agent 决策回合超过最大限制，taskId={}, maxTurns={}",
                    context.getRequest().taskId(), agentProperties.maxTurns());
            return Flux.just(ChatEvent.error(
                    context.getRequest().taskId(),
                    sequence.incrementAndGet(),
                    "MAX_STEPS_EXCEEDED",
                    "Agent 已达到最大决策回合限制"
            ));
        }

        context.incrementStep();
        List<AgentToolCall> accumulatedToolCalls = new ArrayList<>();

        return agentLlmClient.chat(
                        context.getMessages(),
                        toolRegistry.getDefinitions(),
                        context.getRequest().sessionId()
                )
                .flatMap(response -> toChatEvents(context, response, accumulatedToolCalls, sequence))
                .concatWith(Flux.defer(() -> {
                    if (accumulatedToolCalls.isEmpty()) {
                        return Flux.empty();
                    }
                    log.info("Agent 触发工具调用，step={}, toolCount={}, taskId={}",
                            context.getStepCount(), accumulatedToolCalls.size(), context.getRequest().taskId());
                    context.getMessages().add(AgentMessage.assistantWithTools(accumulatedToolCalls));
                    return processToolCalls(context, accumulatedToolCalls, sequence)
                            .concatWith(Flux.defer(() -> executeStep(context, sequence)));
                }));
    }

    private Flux<ChatEvent> toChatEvents(
            AgentTurnContext context,
            AgentModelResponse response,
            List<AgentToolCall> accumulatedToolCalls,
            AtomicLong sequence) {
        if (response.getType() == AgentModelResponse.ResponseType.TOOL_CALL) {
            if (response.getToolCalls() != null) {
                accumulatedToolCalls.addAll(response.getToolCalls());
            }
            return Flux.empty();
        }
        if (response.getType() != AgentModelResponse.ResponseType.FINAL_TEXT) {
            return Flux.empty();
        }

        String textDelta = response.getTextDelta();
        if (textDelta == null || textDelta.isEmpty()) {
            return Flux.empty();
        }
        context.getFullAnswerBuilder().append(textDelta);
        return Flux.just(ChatEvent.text(
                context.getRequest().taskId(),
                sequence.incrementAndGet(),
                textDelta,
                "contentFirst"
        ));
    }

    private Flux<ChatEvent> processToolCalls(
            AgentTurnContext context,
            List<AgentToolCall> toolCalls,
            AtomicLong sequence) {
        List<Flux<ChatEvent>> toolStreams = new ArrayList<>();
        for (AgentToolCall toolCall : toolCalls) {
            toolStreams.add(processSingleToolCall(context, toolCall, sequence));
        }
        return Flux.concat(toolStreams);
    }

    private Flux<ChatEvent> processSingleToolCall(
            AgentTurnContext context,
            AgentToolCall toolCall,
            AtomicLong sequence) {
        String toolCallId = toolCall.getId();
        String toolName = toolCall.getFunction() == null ? "" : toolCall.getFunction().getName();
        String argumentsJson = toolCall.getFunction() == null ? "{}" : toolCall.getFunction().getArguments();
        Optional<AgentTool> toolOptional = toolRegistry.getTool(toolName);

        if (toolOptional.isEmpty()) {
            String observation = "错误：未知工具 " + toolName;
            context.getMessages().add(AgentMessage.tool(toolCallId, toolName, observation));
            return Flux.just(ChatEvent.status(
                    context.getRequest().taskId(),
                    sequence.incrementAndGet(),
                    "尝试调用未知工具：" + toolName
            ));
        }

        if (context.checkAndRecordToolCall(toolName, argumentsJson)) {
            String observation = "已跳过重复的工具调用：" + toolName;
            context.getMessages().add(AgentMessage.tool(toolCallId, toolName, observation));
            return Flux.just(ChatEvent.status(
                    context.getRequest().taskId(),
                    sequence.incrementAndGet(),
                    "已跳过重复查询：" + toolName
            ));
        }

        AgentTool tool = toolOptional.get();
        ChatEvent statusEvent = ChatEvent.status(
                context.getRequest().taskId(),
                sequence.incrementAndGet(),
                "正在查询 " + toolName
        );
        Mono<List<ChatEvent>> execution = tool.execute(argumentsJson, context.getRequest().sessionId())
                .timeout(agentProperties.singleCallTimeout())
                .onErrorResume(ex -> Mono.just(AgentToolResult.failure(
                        toolName, "工具调用超时或异常，请稍后重试")))
                .map(result -> appendToolResult(context, toolCallId, toolName, result, sequence));

        return Flux.just(statusEvent).concatWith(execution.flatMapMany(Flux::fromIterable));
    }

    private List<ChatEvent> appendToolResult(
            AgentTurnContext context,
            String toolCallId,
            String toolName,
            AgentToolResult result,
            AtomicLong sequence) {
        List<ChatEvent> events = new ArrayList<>();
        if (result.getUiNode() != null) {
            events.add(ChatEvent.ui(context.getRequest().taskId(), sequence.incrementAndGet(), result.getUiNode()));
        }
        context.getMessages().add(AgentMessage.tool(
                toolCallId,
                toolName,
                truncateObservation(result.getObservation())
        ));
        return events;
    }

    private String truncateObservation(String observation) {
        if (observation == null) {
            return "";
        }
        int maxChars = agentProperties.observationMaxChars();
        if (observation.length() <= maxChars) {
            return observation;
        }
        return observation.substring(0, maxChars) + "...【已截断】";
    }
}