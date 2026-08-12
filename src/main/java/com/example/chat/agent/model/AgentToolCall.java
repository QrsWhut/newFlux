package com.example.chat.agent.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型输出的工具调用描述对象
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCall {

    /**
     * 工具调用的唯一标识 (例如: call_abc123)
     */
    private String id;

    /**
     * 工具调用类型，固定为 "function"
     */
    @Builder.Default
    private String type = "function";

    /**
     * 调用的具体 Function 描述
     */
    private FunctionCall function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        /**
         * 函数名
         */
        private String name;

        /**
         * 参数 JSON 字符串
         */
        private String arguments;
    }
}
