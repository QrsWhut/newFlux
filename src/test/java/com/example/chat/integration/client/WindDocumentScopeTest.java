package com.example.chat.integration.client;

import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import com.example.chat.common.enums.FinancialDocumentType;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.exception.FinancialProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

/**
 * Wind 文档能力范围测试。
 */
public class WindDocumentScopeTest {

    @Test
    public void testAutoResearchQueryDoesNotMasqueradeAsNews() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        WindFinancialDocumentGateway gateway = new WindFinancialDocumentGateway(windMcpClient);
        FinancialDocumentQuery query = FinancialDocumentQuery.builder()
                .query("查找贵州茅台最新券商研报")
                .documentType(FinancialDocumentType.AUTO)
                .build();

        StepVerifier.create(gateway.search(query))
                .expectErrorMatches(throwable -> throwable instanceof FinancialProviderException exception
                        && exception.getErrorCode() == FinancialProviderErrorCode.OUT_OF_SCOPE)
                .verify();
        Mockito.verifyNoInteractions(windMcpClient);
    }
}
