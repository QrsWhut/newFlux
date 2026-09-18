package com.example.chat.agent.context;

/**
 * 上下文各组成部分的 Token 估算明细。
 *
 * @param systemPromptTokens 系统提示词 Token
 * @param toolDefinitionTokens 工具定义 Token
 * @param summaryTokens 摘要 Token
 * @param uncoveredTurnTokens 未被摘要覆盖的历史轮次 Token
 * @param currentUserTokens 当前用户输入 Token
 * @param pageContextTokens 页面临时上下文 Token
 * @param currentTurnToolTokens 当前轮工具协议与观察 Token
 * @param protocolOverheadTokens 协议固定开销 Token
 */
public record ContextTokenBreakdown(
        int systemPromptTokens,
        int toolDefinitionTokens,
        int summaryTokens,
        int uncoveredTurnTokens,
        int currentUserTokens,
        int pageContextTokens,
        int currentTurnToolTokens,
        int protocolOverheadTokens) {

    /**
     * 校验所有 Token 分段均为非负数。
     */
    public ContextTokenBreakdown {
        if (systemPromptTokens < 0 || toolDefinitionTokens < 0 || summaryTokens < 0
                || uncoveredTurnTokens < 0 || currentUserTokens < 0 || pageContextTokens < 0
                || currentTurnToolTokens < 0 || protocolOverheadTokens < 0) {
            throw new IllegalArgumentException("Token 分段不能为负数");
        }
    }

    /**
     * 计算完整输入 Token。
     *
     * @return 完整输入 Token，超过整型上限时返回整型上限
     */
    public int totalTokens() {
        long total = (long) systemPromptTokens + toolDefinitionTokens + summaryTokens
                + uncoveredTurnTokens + currentUserTokens + pageContextTokens
                + currentTurnToolTokens + protocolOverheadTokens;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 计算不含历史和摘要的理论最小上下文。
     *
     * @return 理论最小上下文 Token
     */
    public int minimumRequiredTokens() {
        long total = (long) systemPromptTokens + toolDefinitionTokens + currentUserTokens
                + pageContextTokens + currentTurnToolTokens + protocolOverheadTokens;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 计算静态上下文 Token。
     *
     * @return 系统提示词和工具定义 Token 之和
     */
    public int staticContextTokens() {
        long total = (long) systemPromptTokens + toolDefinitionTokens;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
