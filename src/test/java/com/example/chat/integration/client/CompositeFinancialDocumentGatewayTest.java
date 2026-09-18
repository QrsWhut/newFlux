package com.example.chat.integration.client;

import com.example.chat.common.dto.financial.FinancialCapabilityResult;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
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
 * 金融文档组合网关测试。
 */
public class CompositeFinancialDocumentGatewayTest {

    @Test
    public void testDefaultModeUsesLegacyOnly() {
        AtomicInteger windCalls = new AtomicInteger();
        FinancialDocumentGateway legacyGateway = query -> Mono.just(result(FinancialProvider.LEGACY));
        FinancialDocumentGateway windGateway = query -> {
            windCalls.incrementAndGet();
            return Mono.just(result(FinancialProvider.WIND));
        };
        CompositeFinancialDocumentGateway gateway = new CompositeFinancialDocumentGateway(
                legacyGateway, windGateway, new WindProperties());

        StepVerifier.create(gateway.search(new FinancialDocumentQuery()))
                .assertNext(result -> assertEquals(FinancialProvider.LEGACY, result.getProvider()))
                .verifyComplete();
        assertEquals(0, windCalls.get());
    }

    @Test
    public void testWindPreferredFallsBackForOutOfScope() {
        AtomicInteger legacyCalls = new AtomicInteger();
        FinancialDocumentGateway legacyGateway = query -> {
            legacyCalls.incrementAndGet();
            return Mono.just(result(FinancialProvider.LEGACY));
        };
        FinancialDocumentGateway windGateway = query -> Mono.error(providerException(
                FinancialProviderErrorCode.OUT_OF_SCOPE));
        WindProperties properties = new WindProperties();
        properties.setProviderMode(FinancialProviderMode.WIND_PREFERRED);
        CompositeFinancialDocumentGateway gateway = new CompositeFinancialDocumentGateway(
                legacyGateway, windGateway, properties);

        StepVerifier.create(gateway.search(new FinancialDocumentQuery()))
                .assertNext(result -> {
                    assertEquals(FinancialProvider.LEGACY, result.getProvider());
                    assertEquals("PROVIDER_FALLBACK", result.getWarnings().get(0).getString("code"));
                })
                .verifyComplete();
        assertEquals(1, legacyCalls.get());
    }

    @Test
    public void testWindPreferredDoesNotHideAuthenticationError() {
        AtomicInteger legacyCalls = new AtomicInteger();
        FinancialDocumentGateway legacyGateway = query -> {
            legacyCalls.incrementAndGet();
            return Mono.just(result(FinancialProvider.LEGACY));
        };
        FinancialDocumentGateway windGateway = query -> Mono.error(providerException(
                FinancialProviderErrorCode.AUTHENTICATION_FAILED));
        WindProperties properties = new WindProperties();
        properties.setProviderMode(FinancialProviderMode.WIND_PREFERRED);
        CompositeFinancialDocumentGateway gateway = new CompositeFinancialDocumentGateway(
                legacyGateway, windGateway, properties);

        StepVerifier.create(gateway.search(new FinancialDocumentQuery()))
                .expectErrorMatches(throwable -> throwable instanceof FinancialProviderException exception
                        && exception.getErrorCode() == FinancialProviderErrorCode.AUTHENTICATION_FAILED)
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
