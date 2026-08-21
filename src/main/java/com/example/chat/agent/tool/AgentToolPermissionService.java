package com.example.chat.agent.tool;

/**
 * Agent 工具权限判定服务。
 */
public interface AgentToolPermissionService {

    /**
     * 判断当前上下文是否允许使用工具。
     *
     * @param tool 工具
     * @param context 调用上下文
     * @return 是否允许
     */
    boolean isAllowed(AgentTool<?> tool, AgentToolContext context);
}
