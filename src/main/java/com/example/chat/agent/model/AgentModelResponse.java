package com.example.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent LLM 响应结果封装类
 * 使用受限枚举区分“工具调用”与“最终文本分片”
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelResponse {

    public enum ResponseType {
        /**
         * 模型输出了一个或多个工具调用请求
         */
        TOOL_CALL,

        /**
         * 模型输出了回答文本分片
         */
        FINAL_TEXT
    }

    private ResponseType type;

    /**
     * 当 type == TOOL_CALL 时包含的工具调用列表
     */
    private List<AgentToolCall> toolCalls;

    /**
     * 当 type == FINAL_TEXT 时包含的文本增量
     */
    private String textDelta;

    /**
     * 当流结束时聚合的完整文本（可选）
     */
    private String fullContent;

    public static AgentModelResponse toolCalls(List<AgentToolCall> toolCalls) {
        return AgentModelResponse.builder()
                .type(ResponseType.TOOL_CALL)
                .toolCalls(toolCalls)
                .build();
    }

    public static AgentModelResponse textDelta(String delta) {
        return AgentModelResponse.builder()
                .type(ResponseType.FINAL_TEXT)
                .textDelta(delta)
                .build();
    }
}
