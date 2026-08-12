package com.example.chat.agent.model;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传递给大模型的 Tool 定义 Schema（符合 OpenAI Function Calling 规范）
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolDefinition {

    @Builder.Default
    private String type = "function";

    private FunctionDefinition function;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionDefinition {
        private String name;
        private String description;
        private JSONObject parameters;
    }
}
