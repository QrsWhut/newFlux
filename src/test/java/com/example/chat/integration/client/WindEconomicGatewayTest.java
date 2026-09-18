package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.enums.FinancialAssetType;
import com.example.chat.common.enums.FinancialDataType;
import com.example.chat.common.enums.FinancialProviderErrorCode;
import com.example.chat.common.enums.WindServerType;
import com.example.chat.common.exception.FinancialProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wind 经济数据参数契约测试。
 */
public class WindEconomicGatewayTest {

    @Test
    public void testEconomicSeriesUsesCamelCaseDateRange() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        Mockito.when(windMcpClient.call(Mockito.any()))
                .thenReturn(Mono.just(economicResult()));
        WindFinancialDataGateway gateway = new WindFinancialDataGateway(windMcpClient);
        FinancialDataQuery query = FinancialDataQuery.builder()
                .question("查询中国 GDP")
                .assetType(FinancialAssetType.ECONOMIC)
                .dataType(FinancialDataType.FUNDAMENTALS)
                .beginDate("2025-01-01")
                .endDate("2025-12-31")
                .build();

        StepVerifier.create(gateway.query(query))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<WindToolCallRequest> captor = ArgumentCaptor.forClass(WindToolCallRequest.class);
        Mockito.verify(windMcpClient).call(captor.capture());
        JSONObject arguments = captor.getValue().getArguments();
        assertEquals("query_economic_indicator_data", captor.getValue().getToolName());
        assertEquals("2025-01-01", arguments.getString("beginDate"));
        assertEquals("2025-12-31", arguments.getString("endDate"));
    }

    @Test
    public void testEconomicSeriesWithoutRangeIsOutOfScope() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        WindFinancialDataGateway gateway = new WindFinancialDataGateway(windMcpClient);
        FinancialDataQuery query = FinancialDataQuery.builder()
                .question("查询中国 GDP")
                .assetType(FinancialAssetType.ECONOMIC)
                .dataType(FinancialDataType.FUNDAMENTALS)
                .build();

        StepVerifier.create(gateway.query(query))
                .expectErrorMatches(throwable -> throwable instanceof FinancialProviderException exception
                        && exception.getErrorCode() == FinancialProviderErrorCode.OUT_OF_SCOPE)
                .verify();
        Mockito.verifyNoInteractions(windMcpClient);
    }

    private WindToolCallResult economicResult() {
        JSONObject structuredContent = new JSONObject(true);
        structuredContent.put("metrics", Collections.emptyList());
        return WindToolCallResult.builder()
                .serverType(WindServerType.ECONOMIC_DATA)
                .toolName("query_economic_indicator_data")
                .structuredContent(structuredContent)
                .warnings(Collections.emptyList())
                .source("万得 Wind 金融数据服务")
                .build();
    }
}
