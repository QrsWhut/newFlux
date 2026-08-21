package com.example.chat.agent;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.tool.AgentToolContext;
import com.example.chat.agent.tool.AgentToolInvoker;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
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

    /** 工具成功状态。 */
    private static final String TOOL_STATUS_SUCCESS = "SUCCESS";
    /** 工具失败状态。 */
    private static final String TOOL_STATUS_FAILED = "FAILED";
    /** 工具跳过状态。 */
    private static final String TOOL_STATUS_SKIPPED = "SKIPPED";

    private final AgentLlmClient agentLlmClient;
    private final AgentToolInvoker toolInvoker;
    private final AgentProperties agentProperties;

    public AgentLoop(
            AgentLlmClient agentLlmClient,
            AgentToolInvoker toolInvoker,
            AgentProperties agentProperties) {
        this.agentLlmClient = agentLlmClient;
        this.toolInvoker = toolInvoker;
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
                        toolInvoker.getAllowedDefinitions(createToolContext(context)),
                        context.getRequest().sessionId()
                )
                .flatMap(response -> toChatEvents(context, response, accumulatedToolCalls, sequence))
                .concatWith(Flux.defer(() -> {
                    if (accumulatedToolCalls.isEmpty()) {
                        return Flux.empty();
                    }
                    log.info("Agent 触发工具调用，step={}, toolCount={}, taskId={}",
                            context.getStepCount(), accumulatedToolCalls.size(), context.getRequest().taskId());
                    AgentMessage toolCallMessage = AgentMessage.assistantWithTools(
                            List.copyOf(accumulatedToolCalls));
                    context.getMessages().add(toolCallMessage);
                    context.getCurrentTurnMessages().add(toolCallMessage);
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
        List<ToolCallTask> tasks = new ArrayList<>(toolCalls.size());
        List<ChatEvent> statusEvents = new ArrayList<>(toolCalls.size());
        for (AgentToolCall toolCall : toolCalls) {
            ToolCallTask task = prepareToolCall(context, toolCall);
            tasks.add(task);
            String statusText = task.duplicate()
                    ? "已跳过重复查询：" + task.toolName()
                    : "正在查询 " + task.toolName();
            statusEvents.add(ChatEvent.status(
                    context.getRequest().taskId(), sequence.incrementAndGet(), statusText));
        }
        int concurrency = Math.min(agentProperties.maxParallelToolCalls(), tasks.size());
        Flux<ChatEvent> executionEvents = Flux.fromIterable(tasks)
                .flatMapSequential(task -> executeToolCall(context, task), concurrency, 1)
                .concatMap(execution -> Flux.fromIterable(
                        appendToolExecution(context, execution, sequence)));
        return Flux.fromIterable(statusEvents).concatWith(executionEvents);
    }

    private ToolCallTask prepareToolCall(AgentTurnContext context, AgentToolCall toolCall) {
        String toolCallId = toolCall.getId();
        String toolName = toolCall.getFunction() == null ? "" : toolCall.getFunction().getName();
        String argumentsJson = toolCall.getFunction() == null ? "{}" : toolCall.getFunction().getArguments();
        boolean duplicate = context.checkAndRecordToolCall(toolName, argumentsJson);
        return new ToolCallTask(toolCallId, toolName, argumentsJson, duplicate);
    }

    private Mono<ToolExecution> executeToolCall(
            AgentTurnContext context, ToolCallTask task) {
        if (task.duplicate()) {
            return Mono.just(new ToolExecution(
                    task.toolCallId(),
                    "已跳过重复的工具调用：" + task.toolName(),
                    TOOL_STATUS_SKIPPED,
                    null));
        }
        return toolInvoker.call(
                        task.toolName(), task.argumentsJson(), createToolContext(context))
                .map(result -> new ToolExecution(
                        task.toolCallId(),
                        result.toModelObservation(),
                        result.isSuccess() ? TOOL_STATUS_SUCCESS : TOOL_STATUS_FAILED,
                        result));
    }

    private List<ChatEvent> appendToolExecution(
            AgentTurnContext context,
            ToolExecution execution,
            AtomicLong sequence) {
        List<ChatEvent> events = new ArrayList<>();
        AgentToolResult result = execution.result();
        if (result != null && result.getUiNode() != null) {
            events.add(ChatEvent.ui(
                    context.getRequest().taskId(),
                    sequence.incrementAndGet(),
                    result.getUiNode()));
        }
        context.getMessages().add(AgentMessage.tool(
                execution.toolCallId(), truncateObservation(execution.observation())));
        context.getCurrentTurnMessages().add(createToolStatusMessage(
                execution.toolCallId(), execution.status()));
        return events;
    }

    private AgentMessage createToolStatusMessage(String toolCallId, String status) {
        JSONObject statusResult = new JSONObject(true);
        statusResult.put("status", status);
        return AgentMessage.tool(toolCallId, statusResult.toJSONString());
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

    private AgentToolContext createToolContext(AgentTurnContext context) {
        return AgentToolContext.builder()
                .taskId(context.getRequest().taskId())
                .sessionId(context.getRequest().sessionId())
                .userId(context.getRequest().userId())
                .attributes(context.getRequest().attributes())
                .build();
    }

    /** 准备完成且尚未执行的工具调用。 */
    private record ToolCallTask(
            String toolCallId,
            String toolName,
            String argumentsJson,
            boolean duplicate) {
    }

    /** 工具执行完成后等待按原始顺序归并的结果。 */
    private record ToolExecution(
            String toolCallId,
            String observation,
            String status,
            AgentToolResult result) {
    }
}
