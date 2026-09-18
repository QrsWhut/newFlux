package com.example.chat.agent.trace;

import com.example.chat.agent.context.ContextBudgetDecision;
import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.agent.model.AgentModelUsage;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.agent.tool.AgentToolError;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.enums.ModelCallStatus;
import com.example.chat.common.enums.ToolCallStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 Turn 的模型与工具执行事实收集器。
 */
public class AgentExecutionTrace {

    /** 当前持久化作用域。 */
    private AgentTraceScope scope;
    /** 模型调用事实。 */
    private final Map<ModelCallTraceHandle, MutableModelCall> modelCalls =
            new LinkedHashMap<>();
    /** 工具调用事实。 */
    private final Map<ToolCallTraceHandle, MutableToolCall> toolCalls =
            new LinkedHashMap<>();

    /**
     * 绑定持久化作用域。
     *
     * @param traceScope 执行作用域
     */
    public synchronized void bindScope(AgentTraceScope traceScope) {
        if (traceScope == null) {
            throw new IllegalArgumentException("执行轨迹作用域不能为空");
        }
        if (scope != null) {
            throw new IllegalStateException("执行轨迹作用域只能绑定一次");
        }
        scope = traceScope;
    }

    /**
     * 登记模型调用。
     *
     * @param agentStepNo Agent 步骤号
     * @param attemptNo 尝试序号
     * @param request 模型请求
     * @param budgetDecision 上下文预算
     * @param tools 工具定义
     * @return 模型调用句柄
     */
    public synchronized ModelCallTraceHandle beginModelCall(
            int agentStepNo,
            int attemptNo,
            AgentModelRequest request,
            ContextBudgetDecision budgetDecision,
            List<AgentToolDefinition> tools) {
        ModelCallTraceHandle handle =
                new ModelCallTraceHandle(agentStepNo, attemptNo);
        if (modelCalls.containsKey(handle)) {
            throw new IllegalStateException("模型调用轨迹重复");
        }
        modelCalls.put(handle, new MutableModelCall(request, budgetDecision));
        return handle;
    }

    /**
     * 完成模型调用。
     *
     * @param handle 模型调用句柄
     * @param events 模型事件
     */
    public synchronized void completeModelCall(
            ModelCallTraceHandle handle,
            List<AgentModelEvent> events) {
        MutableModelCall call = requiredModelCall(handle);
        call.status = ModelCallStatus.SUCCESS;
        call.finishTime = Instant.now();
        if (events == null) {
            return;
        }
        for (AgentModelEvent event : events) {
            if (event == null) {
                continue;
            }
            if (hasText(event.getResponseId())) {
                call.providerRequestId = event.getResponseId();
            }
            if (hasText(event.getProviderCode())) {
                call.providerCode = event.getProviderCode();
            }
            if (hasText(event.getModelName())) {
                call.modelName = event.getModelName();
            }
            if (event.getUsage() != null) {
                call.usage = event.getUsage();
            }
        }
    }

    /**
     * 标记最终显式回答对应的模型调用。
     *
     * @param handle 模型调用句柄
     */
    public synchronized void markFinalModelCall(ModelCallTraceHandle handle) {
        requiredModelCall(handle).finalResponse = true;
    }

    /**
     * 登记工具调用。
     *
     * @param agentStepNo Agent 步骤号
     * @param callNo 步骤内调用序号
     * @param callId 模型工具调用标识
     * @param toolName 工具名称
     * @param argumentsContent 工具参数
     * @param duplicate 是否重复调用
     * @return 工具调用句柄
     */
    public synchronized ToolCallTraceHandle beginToolCall(
            int agentStepNo,
            int callNo,
            String callId,
            String toolName,
            String argumentsContent,
            boolean duplicate) {
        ToolCallTraceHandle handle = new ToolCallTraceHandle(agentStepNo, callNo);
        if (toolCalls.containsKey(handle)) {
            throw new IllegalStateException("工具调用轨迹重复");
        }
        toolCalls.put(
                handle,
                new MutableToolCall(
                        callId,
                        toolName,
                        ExecutionTraceSanitizer.sanitizeArguments(argumentsContent),
                        duplicate));
        return handle;
    }

    /**
     * 完成工具调用。
     *
     * @param handle 工具调用句柄
     * @param result 工具结果
     */
    public synchronized void completeToolCall(
            ToolCallTraceHandle handle,
            AgentToolResult result) {
        MutableToolCall call = requiredToolCall(handle);
        call.finishTime = LocalDateTime.now();
        call.finishInstant = Instant.now();
        if (call.duplicate) {
            call.status = ToolCallStatus.SKIPPED;
            return;
        }
        call.status = result != null && result.isSuccess()
                ? ToolCallStatus.SUCCESS : ToolCallStatus.FAILED;
        call.resultSummary = ExecutionTraceSanitizer.sanitizeToolResult(result);
        AgentToolError error = result == null ? null : result.getError();
        if (error != null) {
            call.errorCode = error.getCode() == null
                    ? null : error.getCode().name();
            call.errorMessage = ExecutionTraceSanitizer.sanitizeErrorMessage(
                    error.getMessage());
        }
    }

    /**
     * 获取不可变执行事实快照。
     *
     * @return 执行事实快照
     */
    public synchronized AgentExecutionTraceSnapshot snapshot() {
        if (scope == null) {
            throw new IllegalStateException("执行轨迹作用域尚未绑定");
        }
        List<ModelCallTraceSnapshot> modelSnapshots = modelCalls.entrySet()
                .stream()
                .map(entry -> toModelSnapshot(entry.getKey(), entry.getValue()))
                .toList();
        List<ToolCallTraceSnapshot> toolSnapshots = toolCalls.entrySet()
                .stream()
                .map(entry -> toToolSnapshot(entry.getKey(), entry.getValue()))
                .toList();
        return new AgentExecutionTraceSnapshot(scope, modelSnapshots, toolSnapshots);
    }

    private ModelCallTraceSnapshot toModelSnapshot(
            ModelCallTraceHandle handle,
            MutableModelCall call) {
        AgentModelUsage usage = call.usage;
        ContextBudgetDecision budget = call.budgetDecision;
        int maxOutputTokens = call.request == null
                || call.request.getMaxOutputTokens() == null
                ? 0 : call.request.getMaxOutputTokens();
        return new ModelCallTraceSnapshot(
                handle.agentStepNo(),
                handle.attemptNo(),
                call.status,
                call.providerRequestId,
                call.providerCode,
                "",
                "openai-responses",
                call.modelName,
                budget == null ? 0 : budget.estimatedInputTokens(),
                integerValue(usage == null ? null : usage.getInputTokens()),
                integerValue(usage == null ? null : usage.getOutputTokens()),
                integerValue(usage == null ? null : usage.getReasoningTokens()),
                integerValue(usage == null ? null : usage.getCachedInputTokens()),
                integerValue(usage == null ? null : usage.getTotalTokens()),
                budget == null ? 0 : budget.hardInputLimit() + maxOutputTokens,
                maxOutputTokens,
                usage != null,
                budget == null ? "" : budget.contextFingerprint(),
                scope.toolDefinitionVersion(),
                elapsedMillis(call.startTime, call.finishTime),
                call.errorCode,
                call.errorMessage,
                call.finalResponse);
    }

    private ToolCallTraceSnapshot toToolSnapshot(
            ToolCallTraceHandle handle,
            MutableToolCall call) {
        return new ToolCallTraceSnapshot(
                handle.agentStepNo(),
                handle.callNo(),
                call.callId,
                call.toolName,
                call.argumentsContent,
                call.status,
                call.resultSummary,
                null,
                call.errorCode,
                call.errorMessage,
                elapsedMillis(call.startInstant, call.finishInstant),
                call.startTime,
                call.finishTime);
    }

    private MutableModelCall requiredModelCall(ModelCallTraceHandle handle) {
        MutableModelCall call = modelCalls.get(handle);
        if (call == null) {
            throw new IllegalArgumentException("模型调用轨迹不存在");
        }
        return call;
    }

    private MutableToolCall requiredToolCall(ToolCallTraceHandle handle) {
        MutableToolCall call = toolCalls.get(handle);
        if (call == null) {
            throw new IllegalArgumentException("工具调用轨迹不存在");
        }
        return call;
    }

    private Integer integerValue(Long value) {
        if (value == null) {
            return null;
        }
        return value >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE : value.intValue();
    }

    private Long elapsedMillis(Instant start, Instant finish) {
        return finish == null ? null : Duration.between(start, finish).toMillis();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 可变模型事实，仅在同步方法内访问。 */
    private static final class MutableModelCall {
        private final AgentModelRequest request;
        private final ContextBudgetDecision budgetDecision;
        private final Instant startTime = Instant.now();
        private ModelCallStatus status = ModelCallStatus.REQUESTING;
        private Instant finishTime;
        private String providerRequestId;
        private String providerCode;
        private String modelName;
        private AgentModelUsage usage;
        private String errorCode;
        private String errorMessage;
        private boolean finalResponse;

        private MutableModelCall(
                AgentModelRequest request,
                ContextBudgetDecision budgetDecision) {
            this.request = request;
            this.budgetDecision = budgetDecision;
            this.providerCode = request == null ? null : request.getProvider();
            this.modelName = request == null ? null : request.getModel();
        }
    }

    /** 可变工具事实，仅在同步方法内访问。 */
    private static final class MutableToolCall {
        private final String callId;
        private final String toolName;
        private final String argumentsContent;
        private final boolean duplicate;
        private final LocalDateTime startTime = LocalDateTime.now();
        private final Instant startInstant = Instant.now();
        private ToolCallStatus status;
        private LocalDateTime finishTime;
        private Instant finishInstant;
        private String resultSummary;
        private String errorCode;
        private String errorMessage;

        private MutableToolCall(
                String callId,
                String toolName,
                String argumentsContent,
                boolean duplicate) {
            this.callId = callId;
            this.toolName = toolName;
            this.argumentsContent = argumentsContent;
            this.duplicate = duplicate;
            this.status = duplicate
                    ? ToolCallStatus.SKIPPED : ToolCallStatus.RUNNING;
        }
    }
}