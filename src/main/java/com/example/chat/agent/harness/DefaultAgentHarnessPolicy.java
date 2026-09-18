package com.example.chat.agent.harness;

import com.alibaba.fastjson.JSON;
import com.example.chat.agent.AgentTurnContext;
import com.example.chat.agent.context.ContextBudgetDecision;
import com.example.chat.agent.context.ContextEstimationInput;
import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.memory.ConversationTurn;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.enums.ContextErrorCode;
import com.example.chat.config.AgentMemoryProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 默认 Harness 策略，负责预算输入分区与拒绝错误映射。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Component
public class DefaultAgentHarnessPolicy implements AgentHarnessPolicy {

    private static final String ATTRIBUTE_MODEL_PROVIDER = "modelProvider";
    private static final String ATTRIBUTE_MODEL = "model";

    /** Agent 记忆与预算配置。 */
    private final AgentMemoryProperties memoryProperties;

    /**
     * 创建默认 Harness 策略。
     *
     * @param memoryProperties Agent 记忆与预算配置
     */
    public DefaultAgentHarnessPolicy(AgentMemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /**
     * 构造覆盖静态提示、历史、当前输入和工具观察的预算输入。
     *
     * @param context 当前执行上下文
     * @param frozenToolDefinitions 本轮冻结的工具定义
     * @return 上下文预算输入
     */
    @Override
    public ContextEstimationInput createEstimationInput(
            AgentTurnContext context,
            List<AgentToolDefinition> frozenToolDefinitions) {
        if (context == null || context.getRequest() == null) {
            throw new IllegalArgumentException("Agent 执行上下文和请求不能为空");
        }
        List<AgentMessage> messages = context.getMessages() == null
                ? Collections.emptyList() : context.getMessages();
        if (context.getBaseEstimationInput() != null) {
            return refreshPreparedEstimationInput(
                    context,
                    messages,
                    frozenToolDefinitions);
        }
        Map<String, Object> attributes = context.getRequest().attributes();
        return new ContextEstimationInput(
                attributeText(attributes, ATTRIBUTE_MODEL_PROVIDER),
                attributeText(attributes, ATTRIBUTE_MODEL),
                systemPrompt(messages),
                toolDefinitions(frozenToolDefinitions),
                memorySummary(context.getMemory()),
                uncoveredTurns(context.getMemory()),
                context.getRequest().question(),
                pageContext(attributes),
                currentTurnToolMessages(context.getCurrentTurnMessages()),
                messages.size());
    }

    private ContextEstimationInput refreshPreparedEstimationInput(
            AgentTurnContext context,
            List<AgentMessage> messages,
            List<AgentToolDefinition> frozenToolDefinitions) {
        ContextEstimationInput baseInput = context.getBaseEstimationInput();
        return new ContextEstimationInput(
                baseInput.providerCode(),
                baseInput.modelName(),
                baseInput.systemPrompt(),
                toolDefinitions(frozenToolDefinitions),
                baseInput.summary(),
                baseInput.uncoveredTurns(),
                baseInput.currentUserContent(),
                baseInput.pageContext(),
                currentTurnToolMessages(context.getCurrentTurnMessages()),
                messages.size());
    }

    /**
     * 将 REJECT 预算转换为稳定错误，其余区域允许执行。
     *
     * @param budgetDecision 上下文预算结果
     * @return Harness 治理决策
     */
    @Override
    public AgentHarnessDecision decide(ContextBudgetDecision budgetDecision) {
        if (budgetDecision == null) {
            throw new IllegalArgumentException("上下文预算结果不能为空");
        }
        int maxOutputTokens = memoryProperties.budget().maxOutputTokens();
        if (budgetDecision.hardLimitExceeded()) {
            ContextErrorCode errorCode = budgetDecision.errorCode() == null
                    ? ContextErrorCode.CONTEXT_LIMIT_EXCEEDED
                    : budgetDecision.errorCode();
            return AgentHarnessDecision.reject(
                    maxOutputTokens,
                    errorCode.getCode(),
                    errorMessage(errorCode));
        }
        return AgentHarnessDecision.allow(maxOutputTokens);
    }

    private String systemPrompt(List<AgentMessage> messages) {
        StringBuilder promptBuilder = new StringBuilder();
        for (AgentMessage message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            if (AgentMessage.ROLE_SYSTEM.equals(message.getRole())
                    || AgentMessage.ROLE_DEVELOPER.equals(message.getRole())) {
                if (!promptBuilder.isEmpty()) {
                    promptBuilder.append('\n');
                }
                promptBuilder.append(message.getContent());
            }
        }
        return promptBuilder.toString();
    }

    private List<String> toolDefinitions(List<AgentToolDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        List<String> serializedDefinitions = new ArrayList<>(definitions.size());
        for (AgentToolDefinition definition : definitions) {
            if (definition != null) {
                serializedDefinitions.add(definition.toResponsesTool().toJSONString());
            }
        }
        return serializedDefinitions;
    }

    private String memorySummary(ConversationMemory memory) {
        return memory == null ? "" : memory.getSummary();
    }

    private List<String> uncoveredTurns(ConversationMemory memory) {
        if (memory == null || memory.getRecentTurns() == null
                || memory.getRecentTurns().isEmpty()) {
            return List.of();
        }
        List<String> turns = new ArrayList<>(memory.getRecentTurns().size());
        for (ConversationTurn turn : memory.getRecentTurns()) {
            if (turn != null) {
                turns.add(JSON.toJSONString(turn.getMessages()));
            }
        }
        return turns;
    }

    private List<String> currentTurnToolMessages(List<AgentMessage> currentTurnMessages) {
        if (currentTurnMessages == null || currentTurnMessages.isEmpty()) {
            return List.of();
        }
        List<String> toolMessages = new ArrayList<>(currentTurnMessages.size());
        for (AgentMessage message : currentTurnMessages) {
            if (message == null) {
                continue;
            }
            boolean toolOutput = AgentMessage.ROLE_TOOL.equals(message.getRole());
            boolean assistantToolCall = AgentMessage.ROLE_ASSISTANT.equals(message.getRole())
                    && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
            if (toolOutput || assistantToolCall) {
                toolMessages.add(JSON.toJSONString(message));
            }
        }
        return toolMessages;
    }

    private String pageContext(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        return JSON.toJSONString(attributes);
    }

    private String attributeText(Map<String, Object> attributes, String attributeName) {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        Object value = attributes.get(attributeName);
        return value == null ? "" : String.valueOf(value);
    }

    private String errorMessage(ContextErrorCode errorCode) {
        return switch (errorCode) {
            case CURRENT_INPUT_TOO_LARGE -> "当前输入内容过长，请缩短后重试";
            case STATIC_CONTEXT_TOO_LARGE -> "当前 Agent 固定上下文超过模型限制";
            case TOOL_RESULT_TOO_LARGE -> "工具返回内容过长，请缩小查询范围后重试";
            case HISTORY_TOO_LONG -> "会话历史过长，请开启新会话后重试";
            default -> "当前上下文超过模型可处理上限，请精简输入后重试";
        };
    }
}
