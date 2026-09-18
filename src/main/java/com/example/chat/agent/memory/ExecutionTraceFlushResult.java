package com.example.chat.agent.memory;

/**
 * 执行事实轨迹的幂等刷新结果。
 *
 * @param modelCallCount 模型调用记录数量
 * @param toolCallCount 工具调用记录数量
 * @param finalMainModelCallId 最终显式回答对应的主模型调用主键
 */
public record ExecutionTraceFlushResult(
        int modelCallCount,
        int toolCallCount,
        Long finalMainModelCallId) {

    /**
     * 校验刷新计数。
     */
    public ExecutionTraceFlushResult {
        if (modelCallCount < 0 || toolCallCount < 0) {
            throw new IllegalArgumentException("执行轨迹刷新计数不能为负数");
        }
    }
}
