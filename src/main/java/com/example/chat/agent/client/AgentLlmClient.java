package com.example.chat.agent.client;

import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolDefinition;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 专门面向 ReAct Agent 的 LLM 客户端接口，支持原生 Function Calling
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public interface AgentLlmClient {

    /**
     * 向 LLM 网关发送消息和可用工具定义，获取流式响应（可能为文本 Delta 或 Tool Calls）
     *
     * @param messages 历史与当前消息列表
     * @param tools    可用的 Tool 定义列表
     * @param sessionId 会话ID（用于日志追踪与脱敏）
     * @return Agent 模型响应流
     */
    Flux<AgentModelResponse> chat(List<AgentMessage> messages, List<AgentToolDefinition> tools, String sessionId);
}
