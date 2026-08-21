package com.example.chat.agent.tool;

import com.example.chat.common.dto.agent.tool.SearchFinancialDocumentsInput;
import com.example.chat.common.exception.DownstreamException;
import com.example.chat.integration.client.RagClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RagAgentTool 单元测试。
 */
public class RagAgentToolTest {

    @Test
    public void testCallSuccess() {
        RagClient mockClient = Mockito.mock(RagClient.class);
        Mockito.when(mockClient.retrieve(Mockito.any()))
                .thenReturn(Mono.just("茅台财报"));
        RagAgentTool tool = new RagAgentTool(mockClient, new AgentToolSchemaGenerator());

        assertEquals("searchFinancialDocuments",
                tool.definition().getFunction().getName());
        StepVerifier.create(tool.call(
                        new SearchFinancialDocumentsInput("茅台"), context()))
                .expectNextMatches(result -> result.isSuccess()
                        && result.getObservation().contains("茅台财报")
                        && "rag-card".equals(result.getUiNode().nodeId()))
                .verifyComplete();
    }

    @Test
    public void testCallDownstreamErrorReturnsStructuredError() {
        RagClient mockClient = Mockito.mock(RagClient.class);
        Mockito.when(mockClient.retrieve(Mockito.any()))
                .thenReturn(Mono.error(downstreamException("RAG")));
        RagAgentTool tool = new RagAgentTool(mockClient, new AgentToolSchemaGenerator());

        StepVerifier.create(tool.call(
                        new SearchFinancialDocumentsInput("宁德时代"), context()))
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

    private DownstreamException downstreamException(String name) {
        return new DownstreamException(name, 503,
                DownstreamException.ErrorType.HTTP_SERVER_ERROR,
                true, true, "服务暂不可用");
    }
}
