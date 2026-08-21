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
        /** 工具名称。 */
        private String name;
        /** 工具用途说明。 */
        private String description;
        /** 工具参数 JSON Schema。 */
        private JSONObject parameters;
        /** 是否启用严格参数 Schema。 */
        private Boolean strict;
    }
}
