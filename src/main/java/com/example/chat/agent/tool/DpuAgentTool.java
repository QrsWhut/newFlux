package com.example.chat.agent.tool;

import com.example.chat.common.dto.UiNode;
import com.example.chat.common.annotation.AgentToolSpec;
import com.example.chat.common.dto.agent.tool.QueryFinancialDataInput;
import com.example.chat.common.dto.downstream.DpuRequest;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.integration.client.DpuClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 查询行情数据与财务指标的 Agent 工具。
 */
@Slf4j
@Component
@AgentToolSpec(
        name = "queryFinancialData",
        description = "查询股票、基金、指数的行情、财务指标与量化计算结果。")
public class DpuAgentTool extends AbstractAgentTool<QueryFinancialDataInput> {

    /** 单次结果最大字符数。 */
    private static final int RESULT_MAX_CHARS = 3000;
    /** DPU 下游客户端。 */
    private final DpuClient dpuClient;

    public DpuAgentTool(DpuClient dpuClient, AgentToolSchemaGenerator schemaGenerator) {
        super(QueryFinancialDataInput.class,
                AgentToolMetadata.authenticatedReadOnly(), schemaGenerator);
        this.dpuClient = dpuClient;
    }

    @Override
    public Mono<AgentToolResult> call(
            QueryFinancialDataInput input, AgentToolContext context) {
        String downstreamQuery = buildDownstreamQuery(input);
        DpuRequest request = new DpuRequest(downstreamQuery, true, context.getSessionId());
        return dpuClient.query(request)
                .map(dpuData -> {
                    String observation = truncate(dpuData);
                    UiNode card = new UiNode("dpu-card", "DPU",
                            Map.of("query", downstreamQuery, "data", observation),
                            System.currentTimeMillis());
                    return AgentToolResult.success(observation, card);
                })
                .onErrorResume(DownstreamException.class, ex -> {
                    log.warn("DPU 工具调用失败: query={}, errorType={}",
                            downstreamQuery, ex.getErrorType());
                    AgentToolError error = AgentToolError.of(
                            AgentToolErrorCode.DOWNSTREAM_UNAVAILABLE,
                            "DPU 行情计算服务暂不可用",
                            ex.isRetryable());
                    return Mono.just(AgentToolResult.failure(error));
                });
    }

    private String buildDownstreamQuery(QueryFinancialDataInput input) {
        StringBuilder queryBuilder = new StringBuilder(input.getQuery().trim());
        List<String> metricNames = input.getMetricNames();
        if (metricNames != null && !metricNames.isEmpty()) {
            queryBuilder.append("，指标：").append(String.join("、", metricNames));
        }
        if (StringUtils.hasText(input.getTimeRange())) {
            queryBuilder.append("，时间范围：").append(input.getTimeRange().trim());
        }
        return queryBuilder.toString();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > RESULT_MAX_CHARS
                ? text.substring(0, RESULT_MAX_CHARS) + "...[截断]" : text;
    }
}
