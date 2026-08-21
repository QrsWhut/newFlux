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

    /** 开发者指令角色。 */
    public static final String ROLE_DEVELOPER = "developer";
    /** 系统指令角色。 */
    public static final String ROLE_SYSTEM = "system";
    /** 用户角色。 */
    public static final String ROLE_USER = "user";
    /** 助手角色。 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 工具结果角色。 */
    public static final String ROLE_TOOL = "tool";

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
     * 创建开发者指令消息。
     *
     * @param content 指令内容
     * @return 开发者消息
     */
    public static AgentMessage developer(String content) {
        return AgentMessage.builder().role(ROLE_DEVELOPER).content(content).build();
    }

    /**
     * 创建系统指令消息。
     *
     * @param content 指令内容
     * @return 系统消息
     */
    public static AgentMessage system(String content) {
        return AgentMessage.builder().role(ROLE_SYSTEM).content(content).build();
    }

    /**
     * 创建用户消息。
     *
     * @param content 用户内容
     * @return 用户消息
     */
    public static AgentMessage user(String content) {
        return AgentMessage.builder().role(ROLE_USER).content(content).build();
    }

    /**
     * 创建助手文本消息。
     *
     * @param content 助手内容
     * @return 助手消息
     */
    public static AgentMessage assistant(String content) {
        return AgentMessage.builder().role(ROLE_ASSISTANT).content(content).build();
    }

    /**
     * 创建助手工具调用消息。
     *
     * @param toolCalls 工具调用列表
     * @return 助手工具调用消息
     */
    public static AgentMessage assistantWithTools(List<AgentToolCall> toolCalls) {
        return AgentMessage.builder().role(ROLE_ASSISTANT).toolCalls(toolCalls).build();
    }

    /**
     * 创建工具结果消息。
     *
     * @param toolCallId 关联的工具调用 ID
     * @param result 工具结果
     * @return 工具消息
     */
    public static AgentMessage tool(String toolCallId, String result) {
        return AgentMessage.builder()
                .role(ROLE_TOOL)
                .toolCallId(toolCallId)
                .content(result)
                .build();
    }
}
