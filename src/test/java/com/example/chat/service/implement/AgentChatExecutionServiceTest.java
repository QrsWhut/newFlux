package com.example.chat.service.implement;

import com.example.chat.agent.AgentLoop;
import com.example.chat.agent.client.AgentLlmClient;
import com.example.chat.agent.memory.ConversationMemoryService;
import com.example.chat.agent.memory.ConversationSummaryService;
import com.example.chat.agent.memory.InMemoryConversationMemoryRepository;
import com.example.chat.agent.model.AgentModelResponse;
import com.example.chat.agent.prompt.AgentPromptFactory;
import com.example.chat.agent.tool.AgentToolRegistry;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.config.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Agent 对话执行服务测试。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class AgentChatExecutionServiceTest {

    private ConversationMemoryService memoryService;
    private AgentLlmClient agentLlmClient;
    private AgentChatExecutionService executionService;

    @BeforeEach
    public void setUp() {
        memoryService = new ConversationMemoryService(
                new InMemoryConversationMemoryRepository(), new ConversationSummaryService());
        agentLlmClient = Mockito.mock(AgentLlmClient.class);
        AgentProperties agentProperties = new AgentProperties(new AgentProperties.LlmProperties(
                4, Duration.ofSeconds(60), Duration.ofSeconds(10), 4000));
        AgentLoop agentLoop = new AgentLoop(
                agentLlmClient,
                new AgentToolRegistry(Collections.emptyList()),
                agentProperties
        );
        executionService = new AgentChatExecutionService(
                memoryService,
                new AgentPromptFactory(),
                agentLoop,
                agentProperties
        );
    }

    @Test
    public void testStreamSuccessAndAppendsMemory() {
        String question = "新能源龙头是谁？";
        String answer = "新能源龙头通常指宁德时代与比亚迪。";
        Mockito.when(agentLlmClient.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.textDelta(answer)));
        ChatRequest request = new ChatRequest(
                "task-1", "session-1", "user-1", question, Collections.emptyList(), Collections.emptyMap(),
                ExecutionMode.AGENT);

        StepVerifier.create(executionService.stream(request))
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .expectNextMatches(event -> event.type() == ChatEventType.COMPLETE)
                .verifyComplete();

        StepVerifier.create(memoryService.getMemory("user-1", "session-1"))
                .expectNextMatches(memory -> memory.getRecentTurns().size() == 1
                        && question.equals(memory.getRecentTurns().get(0).getUserQuestion())
                        && answer.equals(memory.getRecentTurns().get(0).getAssistantAnswer()))
                .verifyComplete();
    }

    @Test
    public void testClarificationTextCompletesAsNormalAnswer() {
        String clarification = "你想查看宁德时代、比亚迪还是其他公司？";
        Mockito.when(agentLlmClient.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.textDelta(clarification)));
        ChatRequest request = new ChatRequest(
                "task-2", "session-2", "user-2", "帮我看看新能源龙头", Collections.emptyList(),
                Collections.emptyMap(), ExecutionMode.AGENT);

        StepVerifier.create(executionService.stream(request))
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA
                        && clarification.equals(((com.example.chat.common.dto.ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> event.type() == ChatEventType.COMPLETE)
                .verifyComplete();
    }

    @Test
    public void testMaxStepsEmitsErrorWithoutComplete() {
        com.example.chat.agent.model.AgentToolCall toolCall =
                com.example.chat.agent.model.AgentToolCall.builder()
                        .id("call-unknown")
                        .type("function")
                        .function(com.example.chat.agent.model.AgentToolCall.FunctionCall.builder()
                                .name("unknownTool")
                                .arguments("{}")
                                .build())
                        .build();
        Mockito.when(agentLlmClient.chat(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Flux.just(AgentModelResponse.toolCalls(Collections.singletonList(toolCall))));
        ChatRequest request = new ChatRequest(
                "task-3", "session-3", "user-3", "测试", Collections.emptyList(), Collections.emptyMap(),
                ExecutionMode.AGENT);

        StepVerifier.create(executionService.stream(request))
                .expectNextCount(4)
                .expectNextMatches(event -> event.type() == ChatEventType.ERROR
                        && "MAX_STEPS_EXCEEDED".equals(
                                ((com.example.chat.common.dto.ChatEvent.ErrorPayload) event.payload()).errorCode()))
                .verifyComplete();
    }

    @Test
    public void testExecutionMode() {
        assertEquals(ExecutionMode.AGENT, executionService.executionMode());
    }
}