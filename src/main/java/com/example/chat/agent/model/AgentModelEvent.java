package com.example.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 供应商无关的类型化模型流事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelEvent {

    /** 模型流事件类型。 */
    public enum EventType {
        /** 文本增量。 */
        OUTPUT_TEXT_DELTA,
        /** 函数调用。 */
        FUNCTION_CALL,
        /** Token 用量。 */
        USAGE,
        /** 响应完成。 */
        COMPLETED
    }

    /** 事件类型。 */
    private EventType type;
    /** 供应商响应标识。 */
    private String responseId;
    /** 实际供应商标识。 */
    private String providerCode;
    /** 实际模型名称。 */
    private String modelName;
    /** 文本增量。 */
    private String textDelta;
    /** 函数调用列表。 */
    private List<AgentToolCall> toolCalls;
    /** Token 用量。 */
    private AgentModelUsage usage;

    /**
     * 创建文本增量事件。
     *
     * @param responseId 响应标识
     * @param delta 文本增量
     * @return 文本事件
     */
    public static AgentModelEvent textDelta(String responseId, String delta) {
        return AgentModelEvent.builder()
                .type(EventType.OUTPUT_TEXT_DELTA)
                .responseId(responseId)
                .textDelta(delta)
                .build();
    }

    /**
     * 创建函数调用事件。
     *
     * @param responseId 响应标识
     * @param toolCalls 函数调用
     * @return 函数调用事件
     */
    public static AgentModelEvent functionCalls(
            String responseId,
            List<AgentToolCall> toolCalls) {
        return AgentModelEvent.builder()
                .type(EventType.FUNCTION_CALL)
                .responseId(responseId)
                .toolCalls(toolCalls)
                .build();
    }

    /**
     * 创建用量事件。
     *
     * @param responseId 响应标识
     * @param usage Token 用量
     * @return 用量事件
     */
    public static AgentModelEvent usage(String responseId, AgentModelUsage usage) {
        return usage(responseId, null, null, usage);
    }

    /**
     * 创建带实际路由信息的用量事件。
     *
     * @param responseId 响应标识
     * @param providerCode 实际供应商标识
     * @param modelName 实际模型名称
     * @param usage Token 用量
     * @return 用量事件
     */
    public static AgentModelEvent usage(
            String responseId,
            String providerCode,
            String modelName,
            AgentModelUsage usage) {
        return AgentModelEvent.builder()
                .type(EventType.USAGE)
                .responseId(responseId)
                .providerCode(providerCode)
                .modelName(modelName)
                .usage(usage)
                .build();
    }

    /**
     * 创建完成事件。
     *
     * @param responseId 响应标识
     * @return 完成事件
     */
    public static AgentModelEvent completed(String responseId) {
        return completed(responseId, null, null);
    }

    /**
     * 创建带实际路由信息的完成事件。
     *
     * @param responseId 响应标识
     * @param providerCode 实际供应商标识
     * @param modelName 实际模型名称
     * @return 完成事件
     */
    public static AgentModelEvent completed(
            String responseId,
            String providerCode,
            String modelName) {
        return AgentModelEvent.builder()
                .type(EventType.COMPLETED)
                .responseId(responseId)
                .providerCode(providerCode)
                .modelName(modelName)
                .build();
    }
}