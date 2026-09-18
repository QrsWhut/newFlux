package com.example.chat.agent.memory;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.common.dto.agent.memory.ConversationToolCallDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.enums.ConversationTurnStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Turn 事实记录确定性展开为模型协议消息。
 */
@Service
public class ConversationTurnReplayService {

    /** 函数工具类型。 */
    private static final String FUNCTION_TOOL_TYPE = "function";
    /** 不可信历史数据标识。 */
    private static final String UNTRUSTED_DATA_MARKER = "untrusted-data";

    /**
     * 按轮次号展开全部终态轮次，执行中轮次不会被回放。
     *
     * @param turns 会话轮次
     * @return 确定性协议消息列表
     */
    public List<AgentMessage> replay(List<ConversationTurnDTO> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        List<ConversationTurnDTO> sortedTurns = turns.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ConversationTurnDTO::turnNo))
                .toList();
        List<AgentMessage> messages = new ArrayList<>();
        for (ConversationTurnDTO turn : sortedTurns) {
            appendTurn(messages, turn);
        }
        return List.copyOf(messages);
    }

    private void appendTurn(List<AgentMessage> messages, ConversationTurnDTO turn) {
        if (turn.status() == ConversationTurnStatus.PROCESSING) {
            return;
        }
        if (turn.status() == ConversationTurnStatus.SUCCESS) {
            appendSuccessfulTurn(messages, turn);
            return;
        }
        if (turn.status() == ConversationTurnStatus.FAILED
                || turn.status() == ConversationTurnStatus.CANCELLED) {
            messages.add(AgentMessage.user(formatTerminalTurn(turn)));
            return;
        }
        throw new IllegalStateException("不支持的轮次状态: " + turn.status());
    }

    private void appendSuccessfulTurn(List<AgentMessage> messages, ConversationTurnDTO turn) {
        if (turn.userContent() == null || turn.userContent().isBlank()) {
            throw new IllegalStateException("成功轮次缺少用户输入，turnNo=" + turn.turnNo());
        }
        if (turn.assistantContent() == null || turn.assistantContent().isBlank()) {
            throw new IllegalStateException("成功轮次缺少助手最终回答，turnNo=" + turn.turnNo());
        }
        messages.add(AgentMessage.user(turn.userContent()));
        Map<Integer, List<ConversationToolCallDTO>> callsByStep = groupCallsByStep(turn.toolCalls());
        for (Map.Entry<Integer, List<ConversationToolCallDTO>> entry : callsByStep.entrySet()) {
            List<ConversationToolCallDTO> stepCalls = entry.getValue();
            messages.add(AgentMessage.assistantWithTools(stepCalls.stream()
                    .map(this::toToolCall)
                    .toList()));
            for (ConversationToolCallDTO toolCall : stepCalls) {
                messages.add(AgentMessage.tool(toolCall.callId(), formatToolOutput(toolCall)));
            }
        }
        messages.add(AgentMessage.assistant(turn.assistantContent()));
    }

    private Map<Integer, List<ConversationToolCallDTO>> groupCallsByStep(
            List<ConversationToolCallDTO> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Map.of();
        }
        List<ConversationToolCallDTO> sortedCalls = toolCalls.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ConversationToolCallDTO::agentStepNo)
                        .thenComparing(ConversationToolCallDTO::callNo))
                .toList();
        Map<Integer, List<ConversationToolCallDTO>> callsByStep = new LinkedHashMap<>();
        for (ConversationToolCallDTO toolCall : sortedCalls) {
            validateToolCall(toolCall);
            callsByStep.computeIfAbsent(toolCall.agentStepNo(), ignored -> new ArrayList<>())
                    .add(toolCall);
        }
        return callsByStep;
    }

    private AgentToolCall toToolCall(ConversationToolCallDTO toolCall) {
        String arguments = toolCall.argumentsContent() == null
                || toolCall.argumentsContent().isBlank() ? "{}" : toolCall.argumentsContent();
        return AgentToolCall.builder()
                .id(toolCall.callId())
                .type(FUNCTION_TOOL_TYPE)
                .function(AgentToolCall.FunctionCall.builder()
                        .name(toolCall.toolName())
                        .arguments(arguments)
                        .build())
                .build();
    }

    private String formatToolOutput(ConversationToolCallDTO toolCall) {
        JSONObject output = new JSONObject(true);
        output.put("status", toolCall.status().name());
        if (toolCall.resultSummary() != null && !toolCall.resultSummary().isBlank()) {
            output.put("resultSummary", toolCall.resultSummary());
        }
        if (toolCall.errorCode() != null && !toolCall.errorCode().isBlank()) {
            output.put("errorCode", toolCall.errorCode());
        }
        return output.toJSONString();
    }

    private String formatTerminalTurn(ConversationTurnDTO turn) {
        String errorCode = turn.errorCode() == null ? "" : turn.errorCode();
        return """
                <historical_turn trust="%s" status="%s" turn_no="%s">
                <user_content>%s</user_content>
                <error_code>%s</error_code>
                </historical_turn>
                """.formatted(
                UNTRUSTED_DATA_MARKER,
                turn.status().name(),
                turn.turnNo(),
                escapeXml(turn.userContent()),
                escapeXml(errorCode)).trim();
    }

    private void validateToolCall(ConversationToolCallDTO toolCall) {
        if (toolCall.agentStepNo() == null || toolCall.callNo() == null
                || toolCall.callId() == null || toolCall.callId().isBlank()
                || toolCall.toolName() == null || toolCall.toolName().isBlank()
                || toolCall.status() == null) {
            throw new IllegalStateException("工具调用记录字段不完整");
        }
    }

    private String escapeXml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
