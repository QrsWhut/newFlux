package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.dto.UiNode;
import com.example.chat.common.dto.downstream.DpuRequest;
import com.example.chat.integration.client.DpuClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 查询行情数据与财务指标的 Agent 工具 (DPU)
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class DpuAgentTool implements AgentTool {

    private final DpuClient dpuClient;

    public DpuAgentTool(DpuClient dpuClient) {
        this.dpuClient = dpuClient;
    }

    @Override
    public String name() {
        return "queryFinancialData";
    }

    @Override
    public AgentToolDefinition definition() {
        JSONObject properties = new JSONObject();

        JSONObject queryProp = new JSONObject();
        queryProp.put("type", "string");
        queryProp.put("description", "用于查询行情与数据计算的搜索词或标的名 (例如 贵州茅台PE)");
        properties.put("query", queryProp);

        JSONObject metricProp = new JSONObject();
        metricProp.put("type", "string");
        metricProp.put("description", "可选的具体指标名称 (例如 市盈率, 营业收入, 换手率)");
        properties.put("metricNames", metricProp);

        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new String[]{"query"});

        AgentToolDefinition.FunctionDefinition fn = AgentToolDefinition.FunctionDefinition.builder()
                .name(name())
                .description("查询股票、基金、指数的实时行情、财务指标与量化计算结果。用于回答具体数值、估值、表现等数据类问题。")
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

            DpuRequest request = new DpuRequest(query.trim(), true, sessionId);
            return dpuClient.query(request)
                    .map(dpuData -> {
                        String truncated = truncate(dpuData, 3000);
                        UiNode card = new UiNode("dpu-card", "DPU", Map.of("query", query, "data", truncated), System.currentTimeMillis());
                        return AgentToolResult.success(name(), truncated, card);
                    })

                    .onErrorResume(ex -> {
                        log.warn("DpuAgentTool 执行失败: query={}, error={}", query, ex.getMessage());
                        return Mono.just(AgentToolResult.failure(name(), "DPU 行情计算服务暂不可用: " + ex.getMessage()));
                    });
        } catch (com.alibaba.fastjson.JSONException ex) {
            log.warn("DpuAgentTool 参数解析失败: args={}, error={}", argumentsJson, ex.getMessage());
            return Mono.just(AgentToolResult.failure(name(), "参数解析失败: " + ex.getMessage()));
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "...[截断]" : text;
    }
}
