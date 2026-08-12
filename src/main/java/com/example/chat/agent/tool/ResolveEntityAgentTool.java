package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.dto.UiNode;
import com.example.chat.integration.client.NerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 金融实体消歧与解析 Agent 工具 (基于 NER 提取候选实体)
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class ResolveEntityAgentTool implements AgentTool {

    private final NerClient nerClient;

    public ResolveEntityAgentTool(NerClient nerClient) {
        this.nerClient = nerClient;
    }

    @Override
    public String name() {
        return "resolveFinancialEntity";
    }

    @Override
    public AgentToolDefinition definition() {
        JSONObject properties = new JSONObject();

        JSONObject entityProp = new JSONObject();
        entityProp.put("type", "string");
        entityProp.put("description", "待确认或消除歧义的金融实体名称/简称/代称 (如 宁德, 茅台)");
        properties.put("entity", entityProp);

        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", new String[]{"entity"});

        AgentToolDefinition.FunctionDefinition fn = AgentToolDefinition.FunctionDefinition.builder()
                .name(name())
                .description("解析不明确的金融实体词、股票简称或代称，返回标准化候选实体信息与股票代码。")
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
            String entity = args != null ? args.getString("entity") : null;
            if (entity == null || entity.trim().isEmpty()) {
                return Mono.just(AgentToolResult.failure(name(), "参数 entity 不能为空"));
            }

            return nerClient.extractEntities(entity.trim(), sessionId)
                    .map(nerData -> {
                        String observation = "实体解析候选结果: " + nerData;
                        UiNode card = new UiNode("ner-card", "NER", Map.of("entity", entity, "data", nerData), System.currentTimeMillis());
                        return AgentToolResult.success(name(), observation, card);
                    })

                    .onErrorResume(ex -> {
                        log.warn("ResolveEntityAgentTool 执行失败: entity={}, error={}", entity, ex.getMessage());
                        return Mono.just(AgentToolResult.failure(name(), "实体解析服务暂不可用: " + ex.getMessage()));
                    });
        } catch (com.alibaba.fastjson.JSONException ex) {
            log.warn("ResolveEntityAgentTool 参数解析失败: args={}, error={}", argumentsJson, ex.getMessage());
            return Mono.just(AgentToolResult.failure(name(), "参数解析失败: " + ex.getMessage()));
        }
    }
}
