package com.example.chat.agent.tool;

import com.example.chat.integration.client.DpuClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DpuAgentTool 单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class DpuAgentToolTest {

    @Test
    public void testExecuteSuccess() {
        DpuClient mockClient = Mockito.mock(DpuClient.class);
        Mockito.when(mockClient.query(Mockito.any())).thenReturn(Mono.just("PE=30.5"));

        DpuAgentTool tool = new DpuAgentTool(mockClient);
        assertEquals("queryFinancialData", tool.name());

        StepVerifier.create(tool.execute("{\"query\":\"茅台市盈率\"}", "sess-1"))
                .expectNextMatches(res -> res.isSuccess()
                        && res.getObservation().contains("PE=30.5")
                        && res.getUiNode() != null
                        && "dpu-card".equals(res.getUiNode().nodeId()))
                .verifyComplete();
    }

    @Test
    public void testExecuteErrorDegraded() {
        DpuClient mockClient = Mockito.mock(DpuClient.class);
        Mockito.when(mockClient.query(Mockito.any())).thenReturn(Mono.error(new RuntimeException("DPU挂了")));

        DpuAgentTool tool = new DpuAgentTool(mockClient);

        StepVerifier.create(tool.execute("{\"query\":\"茅台\"}", "sess-1"))
                .expectNextMatches(res -> !res.isSuccess() && res.getObservation().contains("暂不可用"))
                .verifyComplete();
    }
}
