package com.example.chat.agent.trace;

import com.example.chat.agent.context.ContextBudgetDecision;
import com.example.chat.agent.context.ContextTokenBreakdown;
import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.agent.model.AgentModelUsage;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ContextZone;
import com.example.chat.common.enums.ModelCallStatus;
import com.example.chat.common.enums.ToolCallStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 执行事实轨迹与敏感信息治理测试。
 */
public class AgentExecutionTraceTest {

    /**
     * 验证实际路由、预算、usage、工具摘要和敏感字段均进入受控快照。
     */
    @Test
    public void testCapturesModelAndSanitizedToolFacts() {
        AgentExecutionTrace trace = new AgentExecutionTrace();
        trace.bindScope(new AgentTraceScope(
                10L,
                20L,
                "task-a",
                3,
                2,
                7,
                "prompt-v3",
                "prompt-hash",
                "tools-v2",
                CompressionLevel.NONE));
        ModelCallTraceHandle modelHandle = trace.beginModelCall(
                1,
                1,
                AgentModelRequest.builder()
                        .provider("requested-provider")
                        .model("requested-model")
                        .maxOutputTokens(256)
                        .build(),
                budgetDecision(),
                List.of());
        AgentModelUsage usage = AgentModelUsage.builder()
                .inputTokens(100L)
                .outputTokens(20L)
                .totalTokens(120L)
                .cachedInputTokens(10L)
                .reasoningTokens(5L)
                .build();
        trace.completeModelCall(modelHandle, List.of(
                AgentModelEvent.usage(
                        "response-a",
                        "actual-provider",
                        "actual-model",
                        usage),
                AgentModelEvent.completed(
                        "response-a",
                        "actual-provider",
                        "actual-model")));
        trace.markFinalModelCall(modelHandle);

        ToolCallTraceHandle toolHandle = trace.beginToolCall(
                1,
                1,
                "call-a",
                "queryFinancialData",
                "{\"apiKey\":\"secret-key\",\"symbol\":\"600000.SH\"}",
                false);
        String largeResult = "{\"accessToken\":\"secret-token\",\"data\":\""
                + "x".repeat(6_000) + "\"}";
        trace.completeToolCall(
                toolHandle,
                AgentToolResult.success(largeResult, null));

        AgentExecutionTraceSnapshot snapshot = trace.snapshot();
        ModelCallTraceSnapshot modelCall = snapshot.modelCalls().get(0);
        ToolCallTraceSnapshot toolCall = snapshot.toolCalls().get(0);
        assertEquals(ModelCallStatus.SUCCESS, modelCall.status());
        assertEquals("actual-provider", modelCall.providerCode());
        assertEquals("actual-model", modelCall.modelName());
        assertEquals("response-a", modelCall.providerRequestId());
        assertEquals(100, modelCall.inputTokens());
        assertEquals("fingerprint-a", modelCall.contextFingerprint());
        assertTrue(modelCall.finalResponse());
        assertEquals(ToolCallStatus.SUCCESS, toolCall.status());
        assertFalse(toolCall.argumentsContent().contains("secret-key"));
        assertFalse(toolCall.resultSummary().contains("secret-token"));
        assertTrue(toolCall.resultSummary().length()
                <= ExecutionTraceSanitizer.MAX_RESULT_SUMMARY_LENGTH);
    }

    private ContextBudgetDecision budgetDecision() {
        ContextTokenBreakdown breakdown = new ContextTokenBreakdown(
                10, 20, 30, 40, 50, 0, 0, 5);
        return new ContextBudgetDecision(
                breakdown,
                155,
                1_000,
                700,
                450,
                300,
                85,
                ContextZone.SAFE,
                null,
                "fingerprint-a");
    }
}
