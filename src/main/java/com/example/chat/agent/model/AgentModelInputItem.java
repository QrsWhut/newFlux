package com.example.chat.agent.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OpenAI Responses 协议的输入项。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelInputItem {

    /** 普通对话消息类型。 */
    public static final String TYPE_MESSAGE = "message";

    /** 函数调用类型。 */
    public static final String TYPE_FUNCTION_CALL = "function_call";

    /** 函数调用结果类型。 */
    public static final String TYPE_FUNCTION_CALL_OUTPUT = "function_call_output";

    /** 输入项类型。 */
    private String type;

    /** 消息角色。 */
    private String role;

    /** 消息文本。 */
    private String content;

    /** 函数调用标识。 */
    @JSONField(name = "call_id")
    private String callId;

    /** 函数名称。 */
    private String name;

    /** 函数参数 JSON 字符串。 */
    private String arguments;

    /** 函数调用结果。 */
    private String output;

    /**
     * 将已有对话消息转换为 Responses 输入项。
     *
     * @param messages 对话消息
     * @return Responses 输入项
     */
    public static List<AgentModelInputItem> fromMessages(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentModelInputItem> inputItems = new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            if (message == null) {
                continue;
            }
            appendMessageItem(inputItems, message);
            appendFunctionCallItems(inputItems, message);
        }
        return inputItems;
    }

    private static void appendMessageItem(List<AgentModelInputItem> inputItems, AgentMessage message) {
        if (AgentMessage.ROLE_TOOL.equals(message.getRole())) {
            inputItems.add(AgentModelInputItem.builder()
                    .type(TYPE_FUNCTION_CALL_OUTPUT)
                    .callId(message.getToolCallId())
                    .output(message.getContent())
                    .build());
            return;
        }
        if (StringUtils.hasText(message.getContent())) {
            inputItems.add(AgentModelInputItem.builder()
                    .type(TYPE_MESSAGE)
                    .role(message.getRole())
                    .content(message.getContent())
                    .build());
        }
    }

    private static void appendFunctionCallItems(List<AgentModelInputItem> inputItems, AgentMessage message) {
        if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
            return;
        }
        for (AgentToolCall toolCall : message.getToolCalls()) {
            if (toolCall == null || toolCall.getFunction() == null) {
                continue;
            }
            inputItems.add(AgentModelInputItem.builder()
                    .type(TYPE_FUNCTION_CALL)
                    .callId(toolCall.getId())
                    .name(toolCall.getFunction().getName())
                    .arguments(toolCall.getFunction().getArguments())
                    .build());
        }
    }
}
