package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.RagRequest;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import com.example.chat.common.enums.FinancialProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * 复用现有 RAG 客户端的金融文档网关。
 */
@Component("legacyFinancialDocumentGateway")
public class LegacyFinancialDocumentGateway implements FinancialDocumentGateway {

    /** 现有 RAG 客户端。 */
    private final RagClient ragClient;

    /**
     * 创建现有金融文档网关。
     *
     * @param ragClient 现有 RAG 客户端
     */
    public LegacyFinancialDocumentGateway(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @Override
    public Mono<FinancialCapabilityResult> search(FinancialDocumentQuery query) {
        return Mono.defer(() -> {
            validateQuery(query);
            RagRequest request = new RagRequest(
                    query.getQuery().trim(), query.getSessionId(), 1000, query.resolveTopK());
            return ragClient.retrieve(request)
                    .map(data -> FinancialCapabilityResult.builder()
                            .provider(FinancialProvider.LEGACY)
                            .data(data)
                            .source("现有 RAG 金融文档服务")
                            .warnings(Collections.emptyList())
                            .build());
        });
    }

    private void validateQuery(FinancialDocumentQuery query) {
        if (query == null || query.getQuery() == null || query.getQuery().isBlank()) {
            throw new IllegalArgumentException("金融文档检索问题不能为空");
        }
    }
}
