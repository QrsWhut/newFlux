package com.example.chat.agent.tool;

import com.example.chat.common.dto.UiNode;
import com.example.chat.common.annotation.AgentToolSpec;
import com.example.chat.common.dto.agent.tool.ResolveFinancialEntityInput;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.integration.client.NerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 金融实体消歧与解析 Agent 工具。
 */
@Slf4j
@Component
@AgentToolSpec(
        name = "resolveFinancialEntity",
        description = "解析不明确的金融实体词、股票简称或代称，返回标准化候选实体。")
public class ResolveEntityAgentTool extends AbstractAgentTool<ResolveFinancialEntityInput> {

    /** NER 下游客户端。 */
    private final NerClient nerClient;

    public ResolveEntityAgentTool(NerClient nerClient, AgentToolSchemaGenerator schemaGenerator) {
        super(ResolveFinancialEntityInput.class,
                AgentToolMetadata.authenticatedReadOnly(), schemaGenerator);
        this.nerClient = nerClient;
    }

    @Override
    public Mono<AgentToolResult> call(
            ResolveFinancialEntityInput input, AgentToolContext context) {
        String entity = input.getEntity().trim();
        return nerClient.extractEntities(entity, context.getSessionId())
                .map(nerData -> {
                    String observation = "实体解析候选结果: " + nerData;
                    UiNode card = new UiNode("ner-card", "NER",
                            Map.of("entity", entity, "data", nerData), System.currentTimeMillis());
                    return AgentToolResult.success(observation, card);
                })
                .onErrorResume(DownstreamException.class, ex -> {
                    log.warn("实体解析工具调用失败: entity={}, errorType={}",
                            entity, ex.getErrorType());
                    AgentToolError error = AgentToolError.of(
                            AgentToolErrorCode.DOWNSTREAM_UNAVAILABLE,
                            "实体解析服务暂不可用",
                            ex.isRetryable());
                    return Mono.just(AgentToolResult.failure(error));
                });
    }
}
