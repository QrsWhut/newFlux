package com.example.chat.integration.client;

import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import reactor.core.publisher.Mono;

/**
 * 金融数据查询网关。
 */
public interface FinancialDataGateway {

    /**
     * 查询金融数据。
     *
     * @param query 金融数据查询请求
     * @return 统一金融能力结果
     */
    Mono<FinancialCapabilityResult> query(FinancialDataQuery query);
}
