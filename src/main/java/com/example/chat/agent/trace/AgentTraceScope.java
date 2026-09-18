package com.example.chat.agent.trace;

import com.example.chat.common.enums.CompressionLevel;

/**
 * 单个 Turn 执行轨迹的持久化作用域。
 *
 * @param conversationId 会话主键
 * @param turnId 轮次主键
 * @param taskId 请求幂等标识
 * @param contextVersion 上下文语义版本
 * @param summaryVersion 本次使用的摘要版本
 * @param historyEndTurnNo 完整回放历史终点
 * @param systemPromptVersion 系统提示词版本
 * @param systemPromptHash 系统提示词哈希
 * @param toolDefinitionVersion 工具定义版本
 * @param compressionLevel 本次上下文压缩等级
 */
public record AgentTraceScope(
        Long conversationId,
        Long turnId,
        String taskId,
        int contextVersion,
        int summaryVersion,
        Integer historyEndTurnNo,
        String systemPromptVersion,
        String systemPromptHash,
        String toolDefinitionVersion,
        CompressionLevel compressionLevel) {

    /**
     * 校验并规范化作用域字段。
     */
    public AgentTraceScope {
        if (conversationId == null || turnId == null) {
            throw new IllegalArgumentException("会话主键和轮次主键不能为空");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (contextVersion <= 0 || summaryVersion < 0) {
            throw new IllegalArgumentException("上下文版本和摘要版本不合法");
        }
        systemPromptVersion = normalize(systemPromptVersion);
        systemPromptHash = normalize(systemPromptHash);
        toolDefinitionVersion = normalize(toolDefinitionVersion);
        compressionLevel = compressionLevel == null
                ? CompressionLevel.NONE : compressionLevel;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
