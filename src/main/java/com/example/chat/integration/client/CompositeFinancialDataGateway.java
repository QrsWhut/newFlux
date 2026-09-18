package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.FinancialProviderMode;
import com.example.chat.common.exception.FinancialProviderException;
import com.example.chat.config.WindProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 按运行模式选择现有或 Wind 能力的金融数据组合网关。
 */
@Primary
@Component
public class CompositeFinancialDataGateway implements FinancialDataGateway {

    /** 现有金融数据网关。 */
    private final FinancialDataGateway legacyGateway;
    /** Wind 金融数据网关。 */
    private final FinancialDataGateway windGateway;
    /** Wind 运行配置。 */
    private final WindProperties windProperties;

    /**
     * 创建金融数据组合网关。
     *
     * @param legacyGateway 现有金融数据网关
     * @param windGateway Wind 金融数据网关
     * @param windProperties Wind 运行配置
     */
    public CompositeFinancialDataGateway(
            @Qualifier("legacyFinancialDataGateway") FinancialDataGateway legacyGateway,
            @Qualifier("windFinancialDataGateway") FinancialDataGateway windGateway,
            WindProperties windProperties) {
        this.legacyGateway = legacyGateway;
        this.windGateway = windGateway;
        this.windProperties = windProperties;
    }

    @Override
    public Mono<FinancialCapabilityResult> query(FinancialDataQuery query) {
        FinancialProviderMode mode = windProperties.resolveProviderMode();
        if (mode == FinancialProviderMode.LEGACY_ONLY) {
            return legacyGateway.query(query);
        }
        if (mode == FinancialProviderMode.WIND_ONLY) {
            return windGateway.query(query);
        }
        return windGateway.query(query)
                .onErrorResume(FinancialProviderException.class,
                        exception -> fallbackIfAllowed(query, exception));
    }

    private Mono<FinancialCapabilityResult> fallbackIfAllowed(
            FinancialDataQuery query, FinancialProviderException exception) {
        if (!exception.isFallbackAllowed()) {
            return Mono.error(exception);
        }
        return legacyGateway.query(query)
                .map(result -> result.withWarning(createFallbackWarning(exception.getErrorCode())));
    }

    private JSONObject createFallbackWarning(FinancialProviderErrorCode errorCode) {
        JSONObject warning = new JSONObject(true);
        warning.put("code", "PROVIDER_FALLBACK");
        warning.put("from", "WIND");
        warning.put("to", "LEGACY");
        warning.put("reasonCode", errorCode.name());
        warning.put("message", "Wind 能力不可用，本次已回退现有金融数据服务");
        return warning;
    }
}
