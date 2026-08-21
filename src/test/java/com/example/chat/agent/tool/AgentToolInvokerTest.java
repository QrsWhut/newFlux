package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.common.dto.agent.tool.SearchFinancialDocumentsInput;
import com.example.chat.config.AgentProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

/**
 * Agent 工具统一调用器测试。
 */
public class AgentToolInvokerTest {

    @Test
    public void testCallDeserializesValidatesAndAudits() {
        AgentTool<SearchFinancialDocumentsInput> tool = tool();
        AgentToolPermissionService permissionService = (ignored, context) -> true;
        AgentToolAuditService auditService = Mockito.mock(AgentToolAuditService.class);
        AgentToolInvoker invoker = invoker(List.of(tool), permissionService, auditService);

        StepVerifier.create(invoker.call("searchFinancialDocuments",
                        """
                        {"query":"贵州茅台"}
                        """, context()))
                .expectNextMatches(AgentToolResult::isSuccess)
                .verifyComplete();

        Mockito.verify(tool).call(
                Mockito.argThat(input -> "贵州茅台".equals(input.getQuery())),
                Mockito.any());
        Mockito.verify(auditService).record(
                Mockito.eq("searchFinancialDocuments"),
                Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test
    public void testValidationFailureDoesNotCallBusinessTool() {
        AgentTool<SearchFinancialDocumentsInput> tool = tool();
        AgentToolInvoker invoker = invoker(
                List.of(tool), (ignored, context) -> true,
                Mockito.mock(AgentToolAuditService.class));

        StepVerifier.create(invoker.call(
                        "searchFinancialDocuments", """
                        {"query":""}
                        """, context()))
                .expectNextMatches(result -> !result.isSuccess()
                        && result.getError().getCode()
                        == AgentToolErrorCode.VALIDATION_FAILED)
                .verifyComplete();

        Mockito.verify(tool, Mockito.never()).call(Mockito.any(), Mockito.any());
    }

    @Test
    public void testPermissionFailureDoesNotCallBusinessTool() {
        AgentTool<SearchFinancialDocumentsInput> tool = tool();
        AgentToolInvoker invoker = invoker(
                List.of(tool), (ignored, context) -> false,
                Mockito.mock(AgentToolAuditService.class));

        StepVerifier.create(invoker.call(
                        "searchFinancialDocuments", """
                        {"query":"茅台"}
                        """, context()))
                .expectNextMatches(result -> result.getError().getCode()
                        == AgentToolErrorCode.TOOL_FORBIDDEN)
                .verifyComplete();

        Mockito.verify(tool, Mockito.never()).call(Mockito.any(), Mockito.any());
    }

    @Test
    public void testUnknownArgumentIsRejected() {
        AgentTool<SearchFinancialDocumentsInput> tool = tool();
        AgentToolInvoker invoker = invoker(
                List.of(tool), (ignored, context) -> true,
                Mockito.mock(AgentToolAuditService.class));

        StepVerifier.create(invoker.call(
                        "searchFinancialDocuments", """
                        {"query":"茅台","url":"https://example.com"}
                        """, context()))
                .expectNextMatches(result -> result.getError().getCode()
                        == AgentToolErrorCode.INVALID_ARGUMENTS)
                .verifyComplete();

        Mockito.verify(tool, Mockito.never()).call(Mockito.any(), Mockito.any());
    }

    @SuppressWarnings("unchecked")
    private AgentTool<SearchFinancialDocumentsInput> tool() {
        AgentTool<SearchFinancialDocumentsInput> tool = Mockito.mock(AgentTool.class);
        AgentToolDefinition definition = new AgentToolSchemaGenerator().createDefinition(
                RagAgentTool.class, SearchFinancialDocumentsInput.class);
        Mockito.when(tool.definition()).thenReturn(definition);
        Mockito.when(tool.inputType()).thenReturn(SearchFinancialDocumentsInput.class);
        Mockito.when(tool.metadata()).thenReturn(
                AgentToolMetadata.authenticatedReadOnly());
        Mockito.when(tool.call(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(AgentToolResult.success("ok", null)));
        return tool;
    }

    private AgentToolInvoker invoker(List<AgentTool<?>> tools,
            AgentToolPermissionService permissionService,
            AgentToolAuditService auditService) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        AgentProperties properties = new AgentProperties(
                new AgentProperties.LlmProperties(
                        4, Duration.ofSeconds(60), Duration.ofSeconds(10), 4000));
        return new AgentToolInvoker(new AgentToolRegistry(tools),
                permissionService, auditService, properties, validator);
    }

    private AgentToolContext context() {
        return AgentToolContext.builder()
                .taskId("task-1")
                .sessionId("session-1")
                .userId("user-1")
                .build();
    }
}
