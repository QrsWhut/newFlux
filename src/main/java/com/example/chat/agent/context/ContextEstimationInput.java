package com.example.chat.agent.context;

import java.util.List;

/**
 * 上下文 Token 估算的不可变输入。
 *
 * @param providerCode 模型供应方编码
 * @param modelName 模型名称
 * @param systemPrompt 系统或开发者提示词
 * @param toolDefinitions 本步骤实际发送的工具定义
 * @param summary 会话摘要数据
 * @param uncoveredTurns 摘要终点后的完整历史轮次
 * @param currentUserContent 当前用户输入
 * @param pageContext 当前页面临时上下文
 * @param currentTurnToolMessages 当前轮不可缺少的工具协议消息
 * @param messageCount 实际协议消息数量
 */
public record ContextEstimationInput(
        String providerCode,
        String modelName,
        String systemPrompt,
        List<String> toolDefinitions,
        String summary,
        List<String> uncoveredTurns,
        String currentUserContent,
        String pageContext,
        List<String> currentTurnToolMessages,
        int messageCount) {

    /**
     * 规范化空值并复制集合，避免估算期间输入被修改。
     */
    public ContextEstimationInput {
        providerCode = normalize(providerCode);
        modelName = normalize(modelName);
        systemPrompt = normalize(systemPrompt);
        toolDefinitions = immutableCopy(toolDefinitions);
        summary = normalize(summary);
        uncoveredTurns = immutableCopy(uncoveredTurns);
        currentUserContent = normalize(currentUserContent);
        pageContext = normalize(pageContext);
        currentTurnToolMessages = immutableCopy(currentTurnToolMessages);
        if (messageCount < 0) {
            throw new IllegalArgumentException("messageCount 不能小于 0");
        }
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(ContextEstimationInput::normalize).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
