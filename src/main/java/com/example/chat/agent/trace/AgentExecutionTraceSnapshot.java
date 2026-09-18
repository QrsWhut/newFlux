package com.example.chat.agent.trace;

import java.util.List;

/**
 * 单个 Turn 的模型与工具事实轨迹快照。
 *
 * @param scope 持久化作用域
 * @param modelCalls 模型调用事实
 * @param toolCalls 工具调用事实
 */
public record AgentExecutionTraceSnapshot(
        AgentTraceScope scope,
        List<ModelCallTraceSnapshot> modelCalls,
        List<ToolCallTraceSnapshot> toolCalls) {

    /**
     * 冻结事实列表并校验作用域。
     */
    public AgentExecutionTraceSnapshot {
        if (scope == null) {
            throw new IllegalArgumentException("执行轨迹作用域不能为空");
        }
        modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
