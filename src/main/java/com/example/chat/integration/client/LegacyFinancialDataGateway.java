package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.DpuRequest;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.enums.FinancialProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * 复用现有 DPU 客户端的金融数据网关。
 */
@Component("legacyFinancialDataGateway")
public class LegacyFinancialDataGateway implements FinancialDataGateway {

    /** 现有 DPU 客户端。 */
    private final DpuClient dpuClient;

    /**
     * 创建现有金融数据网关。
     *
     * @param dpuClient 现有 DPU 客户端
     */
    public LegacyFinancialDataGateway(DpuClient dpuClient) {
        this.dpuClient = dpuClient;
    }

    @Override
    public Mono<FinancialCapabilityResult> query(FinancialDataQuery query) {
        return Mono.defer(() -> {
            validateQuery(query);
            String question = buildQuestion(query);
            DpuRequest request = new DpuRequest(question, true, query.getSessionId());
            return dpuClient.query(request)
                    .map(data -> FinancialCapabilityResult.builder()
                            .provider(FinancialProvider.LEGACY)
                            .data(data)
                            .source("现有 DPU 金融数据服务")
                            .warnings(Collections.emptyList())
                            .build());
        });
    }

    private String buildQuestion(FinancialDataQuery query) {
        StringBuilder questionBuilder = new StringBuilder(query.getQuestion().trim());
        List<String> metricNames = query.getMetricNames();
        if (metricNames != null && !metricNames.isEmpty()) {
            questionBuilder.append("，指标：").append(String.join("、", metricNames));
        }
        if (StringUtils.hasText(query.getTimeRange())) {
            questionBuilder.append("，时间范围：").append(query.getTimeRange().trim());
        }
        return questionBuilder.toString();
    }

    private void validateQuery(FinancialDataQuery query) {
        if (query == null || query.getQuestion() == null || query.getQuestion().isBlank()) {
            throw new IllegalArgumentException("金融数据查询问题不能为空");
        }
    }
}
