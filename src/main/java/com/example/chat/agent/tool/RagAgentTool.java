package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.dto.UiNode;
import com.example.chat.common.dto.downstream.RagRequest;
import com.example.chat.integration.client.RagClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 检索金融新闻、研报与背景知识的 Agent 工具 (RAG)
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class RagAgentTool implements AgentTool {

    private final RagClient ragClient;

    public RagAgentTool(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @Override
    public String name() {
        return "searchFinancialDocuments";
    }

    @Override
    public AgentToolDefinition definition() {
        JSONObject properties = new JSONObject();

        JSONObject queryProp = new JSONObject();
        queryProp.put("type", "string");
        queryProp.put("description", "用于检索金融研报、新闻或背景文档的关键查询词");
        properties.put("query", queryProp);

        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new String[]{"query"});

        AgentToolDefinition.FunctionDefinition fn = AgentToolDefinition.FunctionDefinition.builder()
                .name(name())
                .description("搜索金融研报、新闻与背景知识文档。用于解决需要事实依据、最新新闻、产业逻辑分析的问题。")
                .parameters(params)
                .build();

        return AgentToolDefinition.builder()
                .type("function")
                .function(fn)
                .build();
    }

    @Override
    public Mono<AgentToolResult> execute(String argumentsJson, String sessionId) {
        try {
            JSONObject args = JSON.parseObject(argumentsJson);
            String query = args != null ? args.getString("query") : null;
            if (query == null || query.trim().isEmpty()) {
                return Mono.just(AgentToolResult.failure(name(), "参数 query 不能为空"));
            }

            RagRequest request = new RagRequest(query.trim(), sessionId, 1000, 5);
            return ragClient.retrieve(request)
                    .map(ragData -> {
                        String truncated = truncate(ragData, 3000);
                        UiNode card = new UiNode("rag-card", "RAG", Map.of("query", query, "data", truncated), System.currentTimeMillis());
                        return AgentToolResult.success(name(), truncated, card);
                    })

                    .onErrorResume(ex -> {
                        log.warn("RagAgentTool 执行失败: query={}, error={}", query, ex.getMessage());
                        return Mono.just(AgentToolResult.failure(name(), "RAG 检索服务暂不可用: " + ex.getMessage()));
                    });
        } catch (com.alibaba.fastjson.JSONException ex) {
            log.warn("RagAgentTool 参数解析失败: args={}, error={}", argumentsJson, ex.getMessage());
            return Mono.just(AgentToolResult.failure(name(), "参数解析失败: " + ex.getMessage()));
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "...[截断]" : text;
    }
}
