package com.example.chat.agent.tool;

import com.example.chat.config.AgentProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 基于工具启用白名单和用户身份的默认权限服务。
 */
@Component
public class DefaultAgentToolPermissionService implements AgentToolPermissionService {

    /** Agent 配置。 */
    private final AgentProperties agentProperties;

    /**
     * 创建默认权限服务。
     *
     * @param agentProperties Agent 配置
     */
    public DefaultAgentToolPermissionService(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    @Override
    public boolean isAllowed(AgentTool<?> tool, AgentToolContext context) {
        Set<String> enabledTools = agentProperties.enabledTools();
        String toolName = tool.definition().getFunction().getName();
        if (!enabledTools.isEmpty() && !enabledTools.contains(toolName)) {
            return false;
        }
        return !tool.metadata().isAuthenticatedUserRequired()
                || context != null && StringUtils.hasText(context.getUserId());
    }
}
