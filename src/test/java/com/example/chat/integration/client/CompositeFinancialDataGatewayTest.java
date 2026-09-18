package com.example.chat.integration.client;

import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.FinancialProviderMode;
import com.example.chat.common.exception.FinancialProviderException;
import com.example.chat.config.WindProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 金融数据组合网关测试。
 */
public class CompositeFinancialDataGatewayTest {

    @Test
    public void testWindOnlyUsesWindGateway() {
        AtomicInteger legacyCalls = new AtomicInteger();
        FinancialDataGateway legacyGateway = query -> {
            legacyCalls.incrementAndGet();
            return Mono.just(result(FinancialProvider.LEGACY));
        };
        FinancialDataGateway windGateway = query -> Mono.just(result(FinancialProvider.WIND));
        WindProperties properties = new WindProperties();
        properties.setProviderMode(FinancialProviderMode.WIND_ONLY);
        CompositeFinancialDataGateway gateway = new CompositeFinancialDataGateway(
                legacyGateway, windGateway, properties);

        StepVerifier.create(gateway.query(new FinancialDataQuery()))
                .assertNext(result -> assertEquals(FinancialProvider.WIND, result.getProvider()))
                .verifyComplete();
        assertEquals(0, legacyCalls.get());
    }

    @Test
    public void testWindPreferredFallsBackForNetworkError() {
        FinancialDataGateway legacyGateway = query -> Mono.just(result(FinancialProvider.LEGACY));
        FinancialDataGateway windGateway = query -> Mono.error(providerException(
                FinancialProviderErrorCode.NETWORK_ERROR));
        WindProperties properties = new WindProperties();
        properties.setProviderMode(FinancialProviderMode.WIND_PREFERRED);
        CompositeFinancialDataGateway gateway = new CompositeFinancialDataGateway(
                legacyGateway, windGateway, properties);

        StepVerifier.create(gateway.query(new FinancialDataQuery()))
                .assertNext(result -> {
                    assertEquals(FinancialProvider.LEGACY, result.getProvider());
                    assertEquals("NETWORK_ERROR",
                            result.getWarnings().get(0).getString("reasonCode"));
                })
                .verifyComplete();
    }

    @Test
    public void testWindPreferredDoesNotHideQuotaError() {
        AtomicInteger legacyCalls = new AtomicInteger();
        FinancialDataGateway legacyGateway = query -> {
            legacyCalls.incrementAndGet();
            return Mono.just(result(FinancialProvider.LEGACY));
        };
        FinancialDataGateway windGateway = query -> Mono.error(providerException(
                FinancialProviderErrorCode.QUOTA_EXCEEDED));
        WindProperties properties = new WindProperties();
        properties.setProviderMode(FinancialProviderMode.WIND_PREFERRED);
        CompositeFinancialDataGateway gateway = new CompositeFinancialDataGateway(
                legacyGateway, windGateway, properties);

        StepVerifier.create(gateway.query(new FinancialDataQuery()))
                .expectErrorMatches(throwable -> throwable instanceof FinancialProviderException exception
                        && exception.getErrorCode() == FinancialProviderErrorCode.QUOTA_EXCEEDED)
                .verify();
        assertEquals(0, legacyCalls.get());
    }

    private FinancialCapabilityResult result(FinancialProvider provider) {
        return FinancialCapabilityResult.builder()
                .provider(provider)
                .data("{}")
                .source(provider.name())
                .warnings(Collections.emptyList())
                .build();
    }

    private FinancialProviderException providerException(FinancialProviderErrorCode errorCode) {
        return new FinancialProviderException(FinancialProvider.WIND, errorCode, "安全测试错误", false);
    }
}
