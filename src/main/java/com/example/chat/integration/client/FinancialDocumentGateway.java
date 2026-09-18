package com.example.chat.integration.client;

import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import reactor.core.publisher.Mono;

/**
 * 金融文档检索网关。
 */
public interface FinancialDocumentGateway {

    /**
     * 检索金融文档。
     *
     * @param query 金融文档检索请求
     * @return 统一金融能力结果
     */
    Mono<FinancialCapabilityResult> search(FinancialDocumentQuery query);
}
