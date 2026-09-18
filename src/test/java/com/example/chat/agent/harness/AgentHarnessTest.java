package com.example.chat.agent.harness;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.agent.AgentLoop;
import com.example.chat.agent.AgentTurnContext;
import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.context.ContextBudgetDecision;
import com.example.chat.agent.context.ContextBudgetService;
import com.example.chat.agent.context.ContextEstimationInput;
import com.example.chat.agent.context.ContextTokenBreakdown;
import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import com.example.chat.agent.model.AgentModelUsage;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.model.AgentToolDefinition;
import com.example.chat.agent.tool.AgentToolInvoker;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.common.enums.ContextErrorCode;
import com.example.chat.common.enums.ContextZone;
import com.example.chat.config.AgentMemoryProperties;
import com.example.chat.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Agent Harness 治理入口与 ReAct 步骤语义测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
public class AgentHarnessTest {

    /**
     * 验证每步预算、工具冻结、中间文本抑制和 usage 收集。
     */
    @Test
    public void testBudgetEveryStepSuppressesToolStepTextAndCollectsUsage() {
        AgentLlmClient llmClient = Mockito.mock(AgentLlmClient.class);
        AgentToolInvoker toolInvoker = Mockito.mock(AgentToolInvoker.class);
        ContextBudgetService budgetService = Mockito.mock(ContextBudgetService.class);
        AgentHarnessPolicy harnessPolicy = Mockito.mock(AgentHarnessPolicy.class);
        ContextEstimationInput estimationInput = emptyEstimationInput();
        Mockito.when(harnessPolicy.createEstimationInput(Mockito.any(), Mockito.anyList()))
                .thenReturn(estimationInput);
        Mockito.when(harnessPolicy.decide(Mockito.any()))
                .thenReturn(AgentHarnessDecision.allow(256));
        Mockito.when(budgetService.evaluate(estimationInput))
                .thenReturn(safeBudgetDecision());

        AgentToolDefinition toolDefinition = toolDefinition();
        Mockito.when(toolInvoker.getAllowedDefinitions(Mockito.any()))
                .thenReturn(List.of(toolDefinition));
        Mockito.when(toolInvoker.call(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(AgentToolResult.success("PE=30", null)));
        AgentToolCall toolCall = toolCall();
        Mockito.when(llmClient.respond(Mockito.any()))
                .thenReturn(Flux.just(
                        AgentModelEvent.textDelta("resp-1", "不应泄露的中间文本"),
                        AgentModelEvent.functionCalls("resp-1", List.of(toolCall)),
                        AgentModelEvent.usage("resp-1", usage(40L, 5L))))
                .thenReturn(Flux.just(
                        AgentModelEvent.textDelta("resp-2", "最终答案"),
                        AgentModelEvent.usage("resp-2", usage(60L, 7L)),
                        AgentModelEvent.completed("resp-2")));

        AgentTurnContext context = context("查询贵州茅台");
        AgentLoop agentLoop = new AgentLoop(
                llmClient,
                toolInvoker,
                agentProperties(),
                harnessPolicy,
                budgetService);
        AgentHarness harness = new AgentHarness(agentLoop);

        StepVerifier.create(harness.run(context, new AtomicLong()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA
                        && "最终答案".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .verifyComplete();

        ArgumentCaptor<AgentModelRequest> requestCaptor =
                ArgumentCaptor.forClass(AgentModelRequest.class);
        Mockito.verify(llmClient, Mockito.times(2)).respond(requestCaptor.capture());
        List<AgentModelRequest> modelRequests = requestCaptor.getAllValues();
        assertEquals(256, modelRequests.get(0).getMaxOutputTokens());
        assertEquals(256, modelRequests.get(1).getMaxOutputTokens());
        assertSame(modelRequests.get(0).getTools(), modelRequests.get(1).getTools());
        Mockito.verify(toolInvoker).getAllowedDefinitions(Mockito.any());
        Mockito.verify(budgetService, Mockito.times(2)).evaluate(estimationInput);
        Mockito.verify(harnessPolicy, Mockito.times(2)).decide(Mockito.any());
        assertEquals(2, context.getBudgetDecisions().size());
        assertEquals("最终答案", context.getFullAnswerBuilder().toString());
        assertEquals("resp-2", context.getLatestResponseId());
        assertEquals(7L, context.getLatestModelUsage().getOutputTokens());
    }

    /**
     * 验证预算拒绝直接返回结构化错误且不调用模型。
     */
    @Test
    public void testRejectedBudgetEmitsStructuredErrorWithoutModelCall() {
        AgentLlmClient llmClient = Mockito.mock(AgentLlmClient.class);
        AgentToolInvoker toolInvoker = Mockito.mock(AgentToolInvoker.class);
        ContextBudgetService budgetService = Mockito.mock(ContextBudgetService.class);
        AgentHarnessPolicy harnessPolicy = Mockito.mock(AgentHarnessPolicy.class);
        ContextEstimationInput estimationInput = emptyEstimationInput();
        ContextBudgetDecision rejectedDecision = rejectedBudgetDecision();
        Mockito.when(toolInvoker.getAllowedDefinitions(Mockito.any()))
                .thenReturn(Collections.emptyList());
        Mockito.when(harnessPolicy.createEstimationInput(Mockito.any(), Mockito.anyList()))
                .thenReturn(estimationInput);
        Mockito.when(budgetService.evaluate(estimationInput)).thenReturn(rejectedDecision);
        Mockito.when(harnessPolicy.decide(rejectedDecision))
                .thenReturn(AgentHarnessDecision.reject(
                        256,
                        ContextErrorCode.CURRENT_INPUT_TOO_LARGE.getCode(),
                        "当前输入超过模型上下文限制"));
        AgentLoop agentLoop = new AgentLoop(
                llmClient,
                toolInvoker,
                agentProperties(),
                harnessPolicy,
                budgetService);
        AgentHarness harness = new AgentHarness(agentLoop);

        StepVerifier.create(harness.run(context("超长输入"), new AtomicLong()))
                .expectNextMatches(event -> event.type() == ChatEventType.ERROR
                        && ContextErrorCode.CURRENT_INPUT_TOO_LARGE.getCode().equals(
                                ((ChatEvent.ErrorPayload) event.payload()).errorCode()))
                .verifyComplete();

        Mockito.verifyNoInteractions(llmClient);
        Mockito.verify(budgetService).evaluate(estimationInput);
    }

    /**
     * 验证编译后的摘要与历史作为预算基线，并动态替换当前工具消息。
     */
    @Test
    public void testPreparedEstimationInputIsRefreshedWithCurrentToolMessages() {
        ContextEstimationInput baseInput = new ContextEstimationInput(
                "provider-a",
                "model-a",
                "compiled-system",
                List.of("compiled-tool"),
                "compiled-summary",
                List.of("compiled-turn"),
                "compiled-question",
                "compiled-page",
                List.of("stale-tool-message"),
                4);
        AgentTurnContext context = context("原始问题");
        context.setBaseEstimationInput(baseInput);
        AgentMessage assistantToolCall = AgentMessage.assistantWithTools(List.of(toolCall()));
        AgentMessage toolOutput = AgentMessage.tool("call-1", "PE=30");
        context.appendMessage(assistantToolCall);
        context.appendMessage(toolOutput);
        context.appendCurrentTurnMessage(assistantToolCall);
        context.appendCurrentTurnMessage(toolOutput);
        DefaultAgentHarnessPolicy policy = new DefaultAgentHarnessPolicy(
                new AgentMemoryProperties(null, null, null, null));

        ContextEstimationInput refreshedInput = policy.createEstimationInput(
                context,
                List.of(toolDefinition()));

        assertEquals("compiled-summary", refreshedInput.summary());
        assertEquals(List.of("compiled-turn"), refreshedInput.uncoveredTurns());
        assertEquals("compiled-page", refreshedInput.pageContext());
        assertEquals(3, refreshedInput.messageCount());
        assertEquals(2, refreshedInput.currentTurnToolMessages().size());
        assertFalse(refreshedInput.currentTurnToolMessages().contains("stale-tool-message"));
        assertEquals(1, refreshedInput.toolDefinitions().size());
    }

    private AgentTurnContext context(String question) {
        ChatRequest request = new ChatRequest(
                "task-harness",
                "session-harness",
                "user-harness",
                question,
                Collections.emptyList(),
                Map.of());
        return AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>(List.of(AgentMessage.user(question))))
                .currentTurnMessages(new ArrayList<>(List.of(AgentMessage.user(question))))
                .build();
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(new AgentProperties.LlmProperties(
                4,
                Duration.ofSeconds(60),
                Duration.ofSeconds(10),
                4_000));
    }

    private ContextEstimationInput emptyEstimationInput() {
        return new ContextEstimationInput(
                "",
                "",
                "",
                List.of(),
                "",
                List.of(),
                "",
                "",
                List.of(),
                0);
    }

    private ContextBudgetDecision safeBudgetDecision() {
        return budgetDecision(ContextZone.SAFE, null, 20);
    }

    private ContextBudgetDecision rejectedBudgetDecision() {
        return budgetDecision(ContextZone.REJECT, ContextErrorCode.CURRENT_INPUT_TOO_LARGE, 101);
    }

    private ContextBudgetDecision budgetDecision(
            ContextZone zone,
            ContextErrorCode errorCode,
            int estimatedInputTokens) {
        ContextTokenBreakdown breakdown = new ContextTokenBreakdown(
                0, 0, 0, estimatedInputTokens, 0, 0, 0, 0);
        return new ContextBudgetDecision(
                breakdown,
                estimatedInputTokens,
                100,
                70,
                45,
                30,
                0,
                zone,
                errorCode,
                "fingerprint");
    }

    private AgentModelUsage usage(long inputTokens, long outputTokens) {
        return AgentModelUsage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .build();
    }

    private AgentToolCall toolCall() {
        return AgentToolCall.builder()
                .id("call-1")
                .type("function")
                .function(AgentToolCall.FunctionCall.builder()
                        .name("queryFinancialData")
                        .arguments("{}")
                        .build())
                .build();
    }

    private AgentToolDefinition toolDefinition() {
        JSONObject parameters = new JSONObject(true);
        parameters.put("type", "object");
        return AgentToolDefinition.builder()
                .function(AgentToolDefinition.FunctionDefinition.builder()
                        .name("queryFinancialData")
                        .description("查询金融数据")
                        .parameters(parameters)
                        .strict(Boolean.TRUE)
                        .build())
                .build();
    }
}
