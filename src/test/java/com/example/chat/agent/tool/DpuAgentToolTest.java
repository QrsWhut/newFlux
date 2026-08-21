package com.example.chat.agent.tool;

import com.example.chat.common.dto.agent.tool.QueryFinancialDataInput;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.integration.client.DpuClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DpuAgentTool 单元测试。
 */
public class DpuAgentToolTest {

    @Test
    public void testCallUsesAllStronglyTypedFields() {
        DpuClient mockClient = Mockito.mock(DpuClient.class);
        Mockito.when(mockClient.query(Mockito.any())).thenReturn(Mono.just("PE=30.5"));
        DpuAgentTool tool = new DpuAgentTool(mockClient, new AgentToolSchemaGenerator());

        StepVerifier.create(tool.call(new QueryFinancialDataInput(
                        "贵州茅台", List.of("市盈率"), "2025年"), context()))
                .expectNextMatches(result -> result.isSuccess()
                        && result.getObservation().contains("PE=30.5")
                        && "dpu-card".equals(result.getUiNode().nodeId()))
                .verifyComplete();

        ArgumentCaptor<com.example.chat.common.dto.downstream.DpuRequest> captor =
                ArgumentCaptor.forClass(com.example.chat.common.dto.downstream.DpuRequest.class);
        Mockito.verify(mockClient).query(captor.capture());
        assertTrue(captor.getValue().question().contains("市盈率"));
        assertTrue(captor.getValue().question().contains("2025年"));
    }

    @Test
    public void testCallDownstreamErrorReturnsStructuredError() {
        DpuClient mockClient = Mockito.mock(DpuClient.class);
        Mockito.when(mockClient.query(Mockito.any()))
                .thenReturn(Mono.error(new DownstreamException(
                        "DPU", 503, DownstreamException.ErrorType.HTTP_SERVER_ERROR,
                        true, true, "服务暂不可用")));
        DpuAgentTool tool = new DpuAgentTool(mockClient, new AgentToolSchemaGenerator());

        StepVerifier.create(tool.call(new QueryFinancialDataInput(
                        "贵州茅台", null, null), context()))
                .expectNextMatches(result -> !result.isSuccess()
                        && result.getError().getCode()
                        == AgentToolErrorCode.DOWNSTREAM_UNAVAILABLE)
                .verifyComplete();
    }

    private AgentToolContext context() {
        return AgentToolContext.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .userId("user-1")
                .attributes(Collections.emptyMap())
                .build();
    }
}
