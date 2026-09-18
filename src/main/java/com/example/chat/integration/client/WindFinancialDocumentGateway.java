package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.enums.FinancialDocumentType;
import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.WindServerType;
import com.example.chat.common.exception.FinancialProviderException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 使用 Wind 公告和新闻能力的金融文档网关。
 */
@Component("windFinancialDocumentGateway")
public class WindFinancialDocumentGateway implements FinancialDocumentGateway {

    /** 公告意图词。 */
    private static final List<String> ANNOUNCEMENT_KEYWORDS = List.of(
            "公告", "年报", "季报", "招股书", "监管披露", "分红披露");
    /** Wind 文档服务不支持的文档意图词。 */
    private static final List<String> UNSUPPORTED_KEYWORDS = List.of(
            "研报", "研究报告", "背景资料");
    /** Wind MCP 客户端。 */
    private final WindMcpClient windMcpClient;

    /**
     * 创建 Wind 金融文档网关。
     *
     * @param windMcpClient Wind MCP 客户端
     */
    public WindFinancialDocumentGateway(WindMcpClient windMcpClient) {
        this.windMcpClient = windMcpClient;
    }

    @Override
    public Mono<FinancialCapabilityResult> search(FinancialDocumentQuery query) {
        return Mono.defer(() -> {
            validateQuery(query);
            String toolName = resolveToolName(query);
            JSONObject arguments = new JSONObject(true);
            arguments.put("query", query.getQuery().trim());
            arguments.put("top_k", query.resolveTopK());
            WindToolCallRequest request = WindToolCallRequest.builder()
                    .serverType(WindServerType.FINANCIAL_DOCS)
                    .toolName(toolName)
                    .arguments(arguments)
                    .build();
            return windMcpClient.call(request).map(this::toCapabilityResult);
        });
    }

    private String resolveToolName(FinancialDocumentQuery query) {
        FinancialDocumentType documentType = query.resolveDocumentType();
        if (documentType == FinancialDocumentType.ANNOUNCEMENT) {
            return "get_company_announcements";
        }
        if (documentType == FinancialDocumentType.NEWS) {
            return "get_financial_news";
        }
        if (documentType == FinancialDocumentType.RESEARCH
                || documentType == FinancialDocumentType.BACKGROUND
                || containsAny(query.getQuery(), UNSUPPORTED_KEYWORDS)) {
            throw providerException(
                    FinancialProviderErrorCode.OUT_OF_SCOPE,
                    "Wind 文档服务不覆盖研报或通用背景资料");
        }
        return containsAny(query.getQuery(), ANNOUNCEMENT_KEYWORDS)
                ? "get_company_announcements" : "get_financial_news";
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private FinancialCapabilityResult toCapabilityResult(WindToolCallResult result) {
        return FinancialCapabilityResult.builder()
                .provider(FinancialProvider.WIND)
                .data(result.toDataText())
                .source(result.getSource())
                .warnings(result.getWarnings())
                .serverType(result.getServerType().getProtocolName())
                .toolName(result.getToolName())
                .build();
    }

    private void validateQuery(FinancialDocumentQuery query) {
        if (query == null || query.getQuery() == null || query.getQuery().isBlank()) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind 金融文档检索问题不能为空");
        }
        int topK = query.resolveTopK();
        if (topK < 1 || topK > 50) {
            throw providerException(
                    FinancialProviderErrorCode.INVALID_REQUEST,
                    "Wind 金融文档返回条数必须在 1 到 50 之间");
        }
    }

    private FinancialProviderException providerException(
            FinancialProviderErrorCode errorCode, String message) {
        return new FinancialProviderException(FinancialProvider.WIND, errorCode, message, false);
    }
}
