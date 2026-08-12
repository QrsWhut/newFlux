package com.example.chat.agent;

import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.model.AgentToolCall;
import com.example.chat.agent.tool.AgentTool;
import com.example.chat.agent.tool.AgentToolRegistry;
import com.example.chat.agent.tool.AgentToolResult;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.config.AgentProperties;
import org.junit.jupiter.api.Test;
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
                new AgentToolRegistry(Collections.emptyList()),
                agentProperties()
        );
        ChatRequest request = new ChatRequest(
                "task-1", "session-1", "user-1", "你好", Collections.emptyList(), Map.of());
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>())
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

        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.name()).thenReturn("queryFinancialData");
        Mockito.when(tool.definition()).thenReturn(null);
        Mockito.when(tool.execute(Mockito.any(), Mockito.any()))
                .thenReturn(Mono.just(AgentToolResult.success("queryFinancialData", "PE=30", null)));

        AgentLoop agentLoop = new AgentLoop(mockLlm, new AgentToolRegistry(List.of(tool)), agentProperties());
        ChatRequest request = new ChatRequest(
                "task-2", "session-2", "user-2", "贵州茅台 PE", Collections.emptyList(), Map.of());
        AgentTurnContext context = AgentTurnContext.builder()
                .request(request)
                .memory(new ConversationMemory())
                .messages(new ArrayList<>())
                .build();

        StepVerifier.create(agentLoop.run(context, new AtomicLong()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA
                        && "贵州茅台当前市盈率为 30 倍。".equals(
                                ((ChatEvent.TextDelta) event.payload()).content()))
                .verifyComplete();

        assertEquals("贵州茅台当前市盈率为 30 倍。", context.getFullAnswerBuilder().toString());
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(new AgentProperties.LlmProperties(
                4, Duration.ofSeconds(60), Duration.ofSeconds(10), 4000));
    }
}