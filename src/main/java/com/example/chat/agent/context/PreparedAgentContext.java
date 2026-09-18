package com.example.chat.agent.context;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.agent.prompt.PromptSnapshot;

import java.util.List;

/**
 * 上下文编译完成后的不可变模型输入快照。
 *
 * @param conversationId 会话主键
 * @param contextVersion 上下文语义版本
 * @param summaryVersion 摘要版本
 * @param promptSnapshot 系统提示词快照
 * @param messages 确定性协议消息
 * @param toolDefinitions 本步骤冻结的实际工具定义
 * @param estimationInput 与协议消息对应的 Token 估算输入
 */
public record PreparedAgentContext(
        Long conversationId,
        Integer contextVersion,
        Integer summaryVersion,
        PromptSnapshot promptSnapshot,
        List<AgentMessage> messages,
        List<AgentToolDefinition> toolDefinitions,
        ContextEstimationInput estimationInput) {

    /**
     * 深复制可变协议对象并冻结列表。
     */
    public PreparedAgentContext {
        if (promptSnapshot == null || estimationInput == null) {
            throw new IllegalArgumentException("提示词快照和估算输入不能为空");
        }
        contextVersion = contextVersion == null ? 1 : contextVersion;
        summaryVersion = summaryVersion == null ? 0 : summaryVersion;
        messages = messages == null ? List.of() : messages.stream()
                .map(PreparedAgentContext::copyMessage)
                .toList();
        toolDefinitions = toolDefinitions == null ? List.of() : toolDefinitions.stream()
                .map(PreparedAgentContext::copyToolDefinition)
                .toList();
    }

    private static AgentMessage copyMessage(AgentMessage source) {
        List<AgentToolCall> toolCalls = source.getToolCalls() == null
                ? null : source.getToolCalls().stream()
                .map(PreparedAgentContext::copyToolCall)
                .toList();
        return AgentMessage.builder()
                .role(source.getRole())
                .content(source.getContent())
                .toolCallId(source.getToolCallId())
                .toolCalls(toolCalls)
                .build();
    }

    private static AgentToolCall copyToolCall(AgentToolCall source) {
        AgentToolCall.FunctionCall function = source.getFunction() == null
                ? null : AgentToolCall.FunctionCall.builder()
                .name(source.getFunction().getName())
                .arguments(source.getFunction().getArguments())
                .build();
        return AgentToolCall.builder()
                .id(source.getId())
                .type(source.getType())
                .function(function)
                .build();
    }

    private static AgentToolDefinition copyToolDefinition(AgentToolDefinition source) {
        AgentToolDefinition.FunctionDefinition sourceFunction = source.getFunction();
        AgentToolDefinition.FunctionDefinition function = null;
        if (sourceFunction != null) {
            JSONObject parameters = sourceFunction.getParameters() == null
                    ? null : JSON.parseObject(sourceFunction.getParameters().toJSONString());
            function = AgentToolDefinition.FunctionDefinition.builder()
                    .name(sourceFunction.getName())
                    .description(sourceFunction.getDescription())
                    .parameters(parameters)
                    .strict(sourceFunction.getStrict())
                    .build();
        }
        return AgentToolDefinition.builder()
                .type(source.getType())
                .function(function)
                .build();
    }
}
