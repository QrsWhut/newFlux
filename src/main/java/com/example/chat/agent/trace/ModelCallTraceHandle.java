package com.example.chat.agent.trace;

/**
 * 模型调用轨迹的不透明定位句柄。
 *
 * @param agentStepNo Agent 推理步骤号
 * @param attemptNo 当前步骤尝试序号
 */
public record ModelCallTraceHandle(int agentStepNo, int attemptNo) {

    /**
     * 校验模型调用定位字段。
     */
    public ModelCallTraceHandle {
        if (agentStepNo <= 0 || attemptNo <= 0) {
            throw new IllegalArgumentException("模型步骤号和尝试序号必须大于 0");
        }
    }
}
