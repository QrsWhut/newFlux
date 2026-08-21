package com.example.chat.agent.tool;

import com.example.chat.common.dto.UiNode;
import com.example.chat.common.annotation.AgentToolSpec;
import com.example.chat.common.dto.agent.tool.SearchFinancialDocumentsInput;
import com.example.chat.common.dto.downstream.RagRequest;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.integration.client.RagClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 检索金融新闻、研报与背景知识的 Agent 工具。
 */
@Slf4j
@Component
@AgentToolSpec(
        name = "searchFinancialDocuments",
        description = "搜索金融研报、新闻与背景知识文档，用于需要事实依据和产业逻辑的问题。")
public class RagAgentTool extends AbstractAgentTool<SearchFinancialDocumentsInput> {

    /** 单次结果最大字符数。 */
    private static final int RESULT_MAX_CHARS = 3000;
    /** RAG 下游客户端。 */
    private final RagClient ragClient;

    public RagAgentTool(RagClient ragClient, AgentToolSchemaGenerator schemaGenerator) {
        super(SearchFinancialDocumentsInput.class,
                AgentToolMetadata.authenticatedReadOnly(), schemaGenerator);
        this.ragClient = ragClient;
    }

    @Override
    public Mono<AgentToolResult> call(
            SearchFinancialDocumentsInput input, AgentToolContext context) {
        String query = input.getQuery().trim();
        RagRequest request = new RagRequest(query, context.getSessionId(), 1000, 5);
        return ragClient.retrieve(request)
                .map(ragData -> {
                    String observation = truncate(ragData);
                    UiNode card = new UiNode("rag-card", "RAG",
                            Map.of("query", query, "data", observation), System.currentTimeMillis());
                    return AgentToolResult.success(observation, card);
                })
                .onErrorResume(DownstreamException.class, ex -> {
                    log.warn("RAG 工具调用失败: query={}, errorType={}", query, ex.getErrorType());
                    AgentToolError error = AgentToolError.of(
                            AgentToolErrorCode.DOWNSTREAM_UNAVAILABLE,
                            "RAG 检索服务暂不可用",
                            ex.isRetryable());
                    return Mono.just(AgentToolResult.failure(error));
                });
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > RESULT_MAX_CHARS
                ? text.substring(0, RESULT_MAX_CHARS) + "...[截断]" : text;
    }
}
