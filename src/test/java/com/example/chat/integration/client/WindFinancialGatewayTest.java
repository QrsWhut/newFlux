package com.example.chat.integration.client;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.financial.FinancialDataQuery;
import com.example.chat.common.dto.financial.FinancialDocumentQuery;
import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.enums.FinancialAssetType;
import com.example.chat.common.enums.FinancialDataType;
import com.example.chat.common.enums.FinancialDocumentType;
import com.example.chat.common.enums.FinancialProvider;
import com.example.chat.common.enums.WindServerType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wind 金融领域网关路由测试。
 */
public class WindFinancialGatewayTest {

    @Test
    public void testAnnouncementUsesOfficialQueryAndTopKArguments() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        Mockito.when(windMcpClient.call(Mockito.any()))
                .thenReturn(Mono.just(result(
                        WindServerType.FINANCIAL_DOCS, "get_company_announcements")));
        WindFinancialDocumentGateway gateway = new WindFinancialDocumentGateway(windMcpClient);
        FinancialDocumentQuery query = FinancialDocumentQuery.builder()
                .query("贵州茅台 2025 年年度报告")
                .documentType(FinancialDocumentType.ANNOUNCEMENT)
                .topK(3)
                .build();

        StepVerifier.create(gateway.search(query))
                .assertNext(capabilityResult -> {
                    assertEquals(FinancialProvider.WIND, capabilityResult.getProvider());
                    assertEquals("financial_docs", capabilityResult.getServerType());
                })
                .verifyComplete();

        WindToolCallRequest request = captureRequest(windMcpClient);
        assertEquals("get_company_announcements", request.getToolName());
        assertEquals("贵州茅台 2025 年年度报告", request.getArguments().getString("query"));
        assertEquals(3, request.getArguments().getInteger("top_k"));
    }

    @Test
    public void testStockSnapshotUsesPriceIndicatorContract() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        Mockito.when(windMcpClient.call(Mockito.any()))
                .thenReturn(Mono.just(result(
                        WindServerType.STOCK_DATA, "get_stock_price_indicators")));
        WindFinancialDataGateway gateway = new WindFinancialDataGateway(windMcpClient);
        FinancialDataQuery query = FinancialDataQuery.builder()
                .question("查询贵州茅台最新价和涨跌幅")
                .entity("600519.SH")
                .assetType(FinancialAssetType.STOCK)
                .dataType(FinancialDataType.SNAPSHOT)
                .metricNames(List.of("最新成交价", "涨跌幅"))
                .build();

        StepVerifier.create(gateway.query(query))
                .expectNextCount(1)
                .verifyComplete();

        WindToolCallRequest request = captureRequest(windMcpClient);
        assertEquals(WindServerType.STOCK_DATA, request.getServerType());
        assertEquals("get_stock_price_indicators", request.getToolName());
        assertEquals("600519.SH", request.getArguments().getString("windcode"));
        assertEquals("最新成交价,涨跌幅", request.getArguments().getString("indexes"));
    }

    @Test
    public void testAggregationUsesAnalyticsOnly() {
        WindMcpClient windMcpClient = Mockito.mock(WindMcpClient.class);
        Mockito.when(windMcpClient.call(Mockito.any()))
                .thenReturn(Mono.just(result(
                        WindServerType.ANALYTICS_DATA, "get_financial_data")));
        WindFinancialDataGateway gateway = new WindFinancialDataGateway(windMcpClient);
        FinancialDataQuery query = FinancialDataQuery.builder()
                .question("计算 A 股过去一年的平均成交量")
                .dataType(FinancialDataType.AGGREGATION)
                .build();

        StepVerifier.create(gateway.query(query))
                .expectNextCount(1)
                .verifyComplete();

        WindToolCallRequest request = captureRequest(windMcpClient);
        assertEquals(WindServerType.ANALYTICS_DATA, request.getServerType());
        assertEquals("get_financial_data", request.getToolName());
        assertEquals(query.getQuestion(), request.getArguments().getString("question"));
    }

    private WindToolCallRequest captureRequest(WindMcpClient windMcpClient) {
        ArgumentCaptor<WindToolCallRequest> captor = ArgumentCaptor.forClass(WindToolCallRequest.class);
        Mockito.verify(windMcpClient).call(captor.capture());
        return captor.getValue();
    }

    private WindToolCallResult result(WindServerType serverType, String toolName) {
        JSONObject structuredContent = new JSONObject(true);
        structuredContent.put("data", Collections.emptyList());
        return WindToolCallResult.builder()
                .serverType(serverType)
                .toolName(toolName)
                .structuredContent(structuredContent)
                .warnings(Collections.emptyList())
                .source("万得 Wind 金融数据服务")
                .build();
    }
}
