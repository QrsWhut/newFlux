package com.example.chat.agent;

import com.alibaba.fastjson.JSON;
import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.tool.AgentToolInvoker;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.config.AgentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AgentLoop 核心循环与事件输出测试。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class AgentLoopTest {

    @Test
    public void testDirectTextResponseNoTools() {
        AgentLlmClient mockLlm = Mockito.mock(AgentLlmClient.class);
        Mockito.when(mockLlm.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.textDelta("你好，有什么可以帮你？")));

        AgentLoop agentLoop = new AgentLoop(
                mockLlm,
                emptyToolInvoker(),
                agentProperties()
        );
        ChatRequest request = new ChatRequest(
                "task-1", "session-1", "user-1", "你好", Collections.emptyList(), Map.of());
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>())
                .currentTurnMessages(new ArrayList<>(
                        List.of(AgentMessage.user(request.question()))))
                .build();

        StepVerifier.create(agentLoop.run(context, new AtomicLong()))
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA
                        && "你好，有什么可以帮你？".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .verifyComplete();

        assertEquals("你好，有什么可以帮你？", context.getFullAnswerBuilder().toString());
    }

    @Test
    public void testToolCallThenTextResponse() {
        AgentLlmClient mockLlm = Mockito.mock(AgentLlmClient.class);
        AgentToolCall toolCall = AgentToolCall.builder()
                .id("call-1")
                .type("function")
                .function(AgentToolCall.FunctionCall.builder()
                        .name("queryFinancialData")
                        .arguments("{\"query\":\"贵州茅台市盈率\"}")
                        .build())
                .build();
        Mockito.when(mockLlm.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.toolCalls(List.of(toolCall))))
                .thenReturn(Flux.just(AgentModelResponse.textDelta("贵州茅台当前市盈率为 30 倍。")));

        AgentToolInvoker toolInvoker = emptyToolInvoker();
        Mockito.when(toolInvoker.call(
                        Mockito.eq("queryFinancialData"), Mockito.any(), Mockito.any()))
                .thenReturn(reactor.core.publisher.Mono.just(
                        AgentToolResult.success("PE=30", null)));
        AgentLoop agentLoop = new AgentLoop(mockLlm, toolInvoker, agentProperties());
        ChatRequest request = new ChatRequest(
                "task-2", "session-2", "user-2", "贵州茅台 PE", Collections.emptyList(), Map.of());
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>())
                .currentTurnMessages(new ArrayList<>(
                        List.of(AgentMessage.user(request.question()))))
                .build();

        StepVerifier.create(agentLoop.run(context, new AtomicLong()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA
                        && "贵州茅台当前市盈率为 30 倍。".equals(
                                ((ChatEvent.TextDelta) event.payload()).content()))
                .verifyComplete();

        assertEquals("贵州茅台当前市盈率为 30 倍。", context.getFullAnswerBuilder().toString());
        assertEquals(3, context.getCurrentTurnMessages().size());
        assertEquals("user", context.getCurrentTurnMessages().get(0).getRole());
        assertEquals("assistant", context.getCurrentTurnMessages().get(1).getRole());
        assertEquals("queryFinancialData", context.getCurrentTurnMessages().get(1)
                .getToolCalls().get(0).getFunction().getName());
        assertEquals("tool", context.getCurrentTurnMessages().get(2).getRole());
        assertEquals("SUCCESS", JSON.parseObject(
                context.getCurrentTurnMessages().get(2).getContent()).getString("status"));
    }

    @Test
    public void testParallelToolCallsExecuteConcurrentlyAndMergeInOriginalOrder() {
        AgentLlmClient mockLlm = Mockito.mock(AgentLlmClient.class);
        AgentToolCall firstCall = toolCall("call-1", "queryFinancialData");
        AgentToolCall secondCall = toolCall("call-2", "searchFinancialDocuments");
        Mockito.when(mockLlm.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.toolCalls(
                        List.of(firstCall, secondCall))))
                .thenReturn(Flux.just(AgentModelResponse.textDelta("两个工具均已完成。")));

        AtomicInteger subscriptionCount = new AtomicInteger();
        Sinks.One<AgentToolResult> firstResult = Sinks.one();
        Sinks.One<AgentToolResult> secondResult = Sinks.one();
        AgentToolInvoker toolInvoker = emptyToolInvoker();
        Mockito.when(toolInvoker.call(
                        Mockito.eq("queryFinancialData"), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.defer(() -> {
                    subscriptionCount.incrementAndGet();
                    return firstResult.asMono();
                }));
        Mockito.when(toolInvoker.call(
                        Mockito.eq("searchFinancialDocuments"), Mockito.any(), Mockito.any()))
                .thenReturn(Mono.defer(() -> {
                    subscriptionCount.incrementAndGet();
                    return secondResult.asMono();
                }));
        ChatRequest request = new ChatRequest(
                "task-parallel", "session-parallel", "user-parallel", "并发查询",
                Collections.emptyList(), Map.of());
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>())
                .currentTurnMessages(new ArrayList<>(
                        List.of(AgentMessage.user(request.question()))))
                .build();

        StepVerifier.create(new AgentLoop(mockLlm, toolInvoker, agentProperties())
                        .run(context, new AtomicLong()))
                .expectNextCount(2)
                .then(() -> {
                    assertEquals(2, subscriptionCount.get());
                    secondResult.emitValue(
                            AgentToolResult.success("第二个结果", null),
                            Sinks.EmitFailureHandler.FAIL_FAST);
                    firstResult.emitValue(
                            AgentToolResult.success("第一个结果", null),
                            Sinks.EmitFailureHandler.FAIL_FAST);
                })
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .verifyComplete();

        assertEquals("call-1", context.getCurrentTurnMessages().get(2).getToolCallId());
        assertEquals("call-2", context.getCurrentTurnMessages().get(3).getToolCallId());
    }

    private AgentToolCall toolCall(String toolCallId, String toolName) {
        return AgentToolCall.builder()
                .id(toolCallId)
                .type("function")
                .function(AgentToolCall.FunctionCall.builder()
                        .name(toolName)
                        .arguments("{}")
                        .build())
                .build();
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(new AgentProperties.LlmProperties(
                4, Duration.ofSeconds(60), Duration.ofSeconds(10), 4000));
    }

    private AgentToolInvoker emptyToolInvoker() {
        AgentToolInvoker toolInvoker = Mockito.mock(AgentToolInvoker.class);
        Mockito.when(toolInvoker.getAllowedDefinitions(Mockito.any()))
                .thenReturn(Collections.emptyList());
        return toolInvoker;
    }
}
