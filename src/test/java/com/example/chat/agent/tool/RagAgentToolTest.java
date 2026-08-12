package com.example.chat.agent.tool;

import com.example.chat.integration.client.RagClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagAgentTool 单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class RagAgentToolTest {

    @Test
    public void testExecuteSuccess() {
        RagClient mockClient = Mockito.mock(RagClient.class);
        Mockito.when(mockClient.retrieve(Mockito.any())).thenReturn(Mono.just("[{\"title\":\"茅台财报\"}]"));

        RagAgentTool tool = new RagAgentTool(mockClient);
        assertEquals("searchFinancialDocuments", tool.name());

        StepVerifier.create(tool.execute("{\"query\":\"茅台\"}", "sess-1"))
                .expectNextMatches(res -> res.isSuccess()
                        && res.getObservation().contains("茅台财报")
                        && res.getUiNode() != null
                        && "rag-card".equals(res.getUiNode().nodeId()))
                .verifyComplete();
    }

    @Test
    public void testExecuteEmptyQueryFailure() {
        RagClient mockClient = Mockito.mock(RagClient.class);
        RagAgentTool tool = new RagAgentTool(mockClient);

        StepVerifier.create(tool.execute("{}", "sess-1"))
                .expectNextMatches(res -> !res.isSuccess() && res.getObservation().contains("不能为空"))
                .verifyComplete();
    }

    @Test
    public void testExecuteDownstreamErrorDegraded() {
        RagClient mockClient = Mockito.mock(RagClient.class);
        Mockito.when(mockClient.retrieve(Mockito.any())).thenReturn(Mono.error(new RuntimeException("网络超时")));

        RagAgentTool tool = new RagAgentTool(mockClient);

        StepVerifier.create(tool.execute("{\"query\":\"宁德时代\"}", "sess-1"))
                .expectNextMatches(res -> !res.isSuccess() && res.getObservation().contains("暂不可用"))
                .verifyComplete();
    }
}
