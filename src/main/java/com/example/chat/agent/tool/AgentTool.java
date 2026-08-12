package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;
import reactor.core.publisher.Mono;

/**
 * Agent 统一工具接口
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public interface AgentTool {

    /**
     * 工具名称，必须唯一且遵循 lowerCamelCase (如 searchFinancialDocuments)
     *
     * @return 工具名
     */
    String name();

    /**
     * 获取暴露给 LLM 的 Schema 定义
     *
     * @return AgentToolDefinition
     */
    AgentToolDefinition definition();

    /**
     * 执行工具（非阻塞 Reactor Mono）
     *
     * @param argumentsJson 模型传入的参数 JSON 字符串
     * @param sessionId     会话 ID
     * @return 工具执行结果 Mono
     */
    Mono<AgentToolResult> execute(String argumentsJson, String sessionId);
}
