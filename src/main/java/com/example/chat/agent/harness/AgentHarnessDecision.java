package com.example.chat.agent.harness;

/**
 * Agent Harness 单步治理决策。
 *
 * @param action 治理动作
 * @param maxOutputTokens 本次最大输出 Token
 * @param errorCode 拒绝时的稳定错误码
 * @param errorMessage 拒绝时的用户提示
 * @author Codex
 * @since 2026-08-25
 */
public record AgentHarnessDecision(
        AgentHarnessAction action,
        int maxOutputTokens,
        String errorCode,
        String errorMessage) {

    /**
     * 校验治理决策。
     */
    public AgentHarnessDecision {
        if (action == null) {
            throw new IllegalArgumentException("Harness 治理动作不能为空");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("最大输出 Token 必须大于 0");
        }
        if (action == AgentHarnessAction.REJECT
                && (errorCode == null || errorCode.isBlank()
                || errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("Harness 拒绝决策必须包含错误码和用户提示");
        }
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /**
     * 创建允许决策。
     *
     * @param maxOutputTokens 本次最大输出 Token
     * @return 允许决策
     */
    public static AgentHarnessDecision allow(int maxOutputTokens) {
        return new AgentHarnessDecision(
                AgentHarnessAction.ALLOW,
                maxOutputTokens,
                "",
                "");
    }

    /**
     * 创建拒绝决策。
     *
     * @param maxOutputTokens 本次最大输出 Token
     * @param errorCode 稳定错误码
     * @param errorMessage 用户提示
     * @return 拒绝决策
     */
    public static AgentHarnessDecision reject(
            int maxOutputTokens,
            String errorCode,
            String errorMessage) {
        return new AgentHarnessDecision(
                AgentHarnessAction.REJECT,
                maxOutputTokens,
                errorCode,
                errorMessage);
    }

    /**
     * 判断是否允许模型调用。
     *
     * @return 允许时返回 true
     */
    public boolean allowed() {
        return action == AgentHarnessAction.ALLOW;
    }
}
