package com.example.chat.agent.tool;

/**
 * Agent 工具调用审计服务。
 */
public interface AgentToolAuditService {

    /**
     * 记录一次工具调用结果，不记录原始参数和返回正文。
     *
     * @param toolName 工具名称
     * @param context 调用上下文
     * @param result 调用结果
     * @param durationMillis 耗时毫秒数
     */
    void record(String toolName, AgentToolContext context,
            AgentToolResult result, long durationMillis);
}
