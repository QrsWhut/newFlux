package com.example.chat.agent.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 对话消息模型
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {

    /**
     * 角色: system, user, assistant, tool
     */
    private String role;

    /**
     * 消息文本内容
     */
    private String content;

    /**
     * 当 role=assistant 且模型触发工具调用时的工具列表
     */
    @JSONField(name = "tool_calls")
    private List<AgentToolCall> toolCalls;

    /**
     * 当 role=tool 时，关联的工具调用 ID
     */
    @JSONField(name = "tool_call_id")
    private String toolCallId;

    /**
     * 当 role=tool 时，工具名称
     */
    private String name;

    public static AgentMessage system(String content) {
        return AgentMessage.builder().role("system").content(content).build();
    }

    public static AgentMessage user(String content) {
        return AgentMessage.builder().role("user").content(content).build();
    }

    public static AgentMessage assistant(String content) {
        return AgentMessage.builder().role("assistant").content(content).build();
    }

    public static AgentMessage assistantWithTools(List<AgentToolCall> toolCalls) {
        return AgentMessage.builder().role("assistant").toolCalls(toolCalls).build();
    }

    public static AgentMessage tool(String toolCallId, String name, String result) {
        return AgentMessage.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(result)
                .build();
    }
}
