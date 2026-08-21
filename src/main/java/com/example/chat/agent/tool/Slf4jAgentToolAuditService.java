package com.example.chat.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 SLF4J 的安全审计实现。
 */
@Slf4j
@Component
public class Slf4jAgentToolAuditService implements AgentToolAuditService {

    @Override
    public void record(String toolName, AgentToolContext context,
            AgentToolResult result, long durationMillis) {
        String errorCode = result.getError() == null
                ? "" : result.getError().getCode().name();
        log.info("Agent 工具审计: toolName={}, success={}, errorCode={}, durationMs={}, "
                        + "taskId={}, userId={}, sessionId={}",
                toolName, result.isSuccess(), errorCode, durationMillis,
                context == null ? "" : context.getTaskId(),
                context == null ? "" : mask(context.getUserId()),
                context == null ? "" : mask(context.getSessionId()));
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}
