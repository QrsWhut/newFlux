package com.example.chat.agent.trace;

/**
 * 工具调用轨迹的不透明定位句柄。
 *
 * @param agentStepNo Agent 推理步骤号
 * @param callNo 步骤内调用顺序号
 */
public record ToolCallTraceHandle(int agentStepNo, int callNo) {

    /**
     * 校验工具调用定位字段。
     */
    public ToolCallTraceHandle {
        if (agentStepNo <= 0 || callNo <= 0) {
            throw new IllegalArgumentException("工具步骤号和调用顺序号必须大于 0");
        }
    }
}
