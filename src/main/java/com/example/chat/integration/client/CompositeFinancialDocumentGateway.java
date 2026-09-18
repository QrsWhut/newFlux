package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.FinancialProviderMode;
import com.example.chat.common.exception.FinancialProviderException;
import com.example.chat.config.WindProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 按运行模式选择现有或 Wind 能力的金融文档组合网关。
 */
@Primary
@Component
public class CompositeFinancialDocumentGateway implements FinancialDocumentGateway {

    /** 现有金融文档网关。 */
    private final FinancialDocumentGateway legacyGateway;
    /** Wind 金融文档网关。 */
    private final FinancialDocumentGateway windGateway;
    /** Wind 运行配置。 */
    private final WindProperties windProperties;

    /**
     * 创建金融文档组合网关。
     *
     * @param legacyGateway 现有金融文档网关
     * @param windGateway Wind 金融文档网关
     * @param windProperties Wind 运行配置
     */
    public CompositeFinancialDocumentGateway(
            @Qualifier("legacyFinancialDocumentGateway") FinancialDocumentGateway legacyGateway,
            @Qualifier("windFinancialDocumentGateway") FinancialDocumentGateway windGateway,
            WindProperties windProperties) {
        this.legacyGateway = legacyGateway;
        this.windGateway = windGateway;
        this.windProperties = windProperties;
    }

    @Override
    public Mono<FinancialCapabilityResult> search(FinancialDocumentQuery query) {
        FinancialProviderMode mode = windProperties.resolveProviderMode();
        if (mode == FinancialProviderMode.LEGACY_ONLY) {
            return legacyGateway.search(query);
        }
        if (mode == FinancialProviderMode.WIND_ONLY) {
            return windGateway.search(query);
        }
        return windGateway.search(query)
                .onErrorResume(FinancialProviderException.class,
                        exception -> fallbackIfAllowed(query, exception));
    }

    private Mono<FinancialCapabilityResult> fallbackIfAllowed(
            FinancialDocumentQuery query, FinancialProviderException exception) {
        if (!exception.isFallbackAllowed()) {
            return Mono.error(exception);
        }
        return legacyGateway.search(query)
                .map(result -> result.withWarning(createFallbackWarning(exception.getErrorCode())));
    }

    private JSONObject createFallbackWarning(FinancialProviderErrorCode errorCode) {
        JSONObject warning = new JSONObject(true);
        warning.put("code", "PROVIDER_FALLBACK");
        warning.put("from", "WIND");
        warning.put("to", "LEGACY");
        warning.put("reasonCode", errorCode.name());
        warning.put("message", "Wind 能力不可用，本次已回退现有金融文档服务");
        return warning;
    }
}
