package com.example.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 与具体供应商解耦的模型请求。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelRequest {

    /** 指定的供应商标识；为空时使用默认路由。 */
    private String provider;

    /** 指定的模型标识；为空时使用供应商默认模型。 */
    private String model;

    /** Responses 协议输入项。 */
    private List<AgentModelInputItem> input;

    /** 模型可调用的函数定义。 */
    private List<AgentToolDefinition> tools;

    /** 会话标识，仅用于链路追踪。 */
    private String sessionId;

    /** 是否请求流式输出。 */
    private Boolean stream;

    /** 最大输出 Token 数。 */
    private Integer maxOutputTokens;

    /**
     * 从现有 Agent 对话参数创建 Responses 请求。
     *
     * @param messages 对话消息
     * @param tools 工具定义
     * @param sessionId 会话标识
     * @return 模型请求
     */
    public static AgentModelRequest fromMessages(
            List<AgentMessage> messages,
            List<AgentToolDefinition> tools,
            String sessionId) {
        return AgentModelRequest.builder()
                .input(AgentModelInputItem.fromMessages(messages))
                .tools(tools)
                .sessionId(sessionId)
                .stream(Boolean.TRUE)
                .build();
    }
}
