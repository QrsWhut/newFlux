package com.example.chat.service.implement;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.service.interf.ChatExecutionService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatExecutionRouter 路由分发单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class ChatExecutionRouterTest {

    @Test
    public void testRouteWorkflow() {
        ChatExecutionService workflowService = new StubExecutionService(ExecutionMode.WORKFLOW, "workflow-event");
        ChatExecutionService agentService = new StubExecutionService(ExecutionMode.AGENT, "agent-event");

        ChatExecutionRouter router = new ChatExecutionRouter(List.of(workflowService, agentService));

        ChatRequest request = new ChatRequest("t1", "s1", "u1", "q1", Collections.emptyList(), Collections.emptyMap(), ExecutionMode.WORKFLOW);

        StepVerifier.create(router.stream(request))
                .expectNextMatches(event -> "workflow-event".equals(event.payload()))
                .verifyComplete();
    }

    @Test
    public void testRouteAgent() {
        ChatExecutionService workflowService = new StubExecutionService(ExecutionMode.WORKFLOW, "workflow-event");
        ChatExecutionService agentService = new StubExecutionService(ExecutionMode.AGENT, "agent-event");

        ChatExecutionRouter router = new ChatExecutionRouter(List.of(workflowService, agentService));

        ChatRequest request = new ChatRequest("t2", "s2", "u2", "q2", Collections.emptyList(), Collections.emptyMap(), ExecutionMode.AGENT);

        StepVerifier.create(router.stream(request))
                .expectNextMatches(event -> "agent-event".equals(event.payload()))
                .verifyComplete();
    }

    @Test
    public void testRouteMissingModeThrowsError() {
        ChatExecutionService workflowService = new StubExecutionService(ExecutionMode.WORKFLOW, "workflow-event");
        ChatExecutionRouter router = new ChatExecutionRouter(List.of(workflowService));

        ChatRequest request = new ChatRequest("t3", "s3", "u3", "q3", Collections.emptyList(), Collections.emptyMap(), ExecutionMode.AGENT);

        StepVerifier.create(router.stream(request))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    public void testDuplicateRegistrationThrowsStateError() {
        ChatExecutionService s1 = new StubExecutionService(ExecutionMode.WORKFLOW, "e1");
        ChatExecutionService s2 = new StubExecutionService(ExecutionMode.WORKFLOW, "e2");

        assertThrows(IllegalStateException.class, () -> new ChatExecutionRouter(List.of(s1, s2)));
    }

    private static class StubExecutionService implements ChatExecutionService {
        private final ExecutionMode mode;
        private final String eventPayload;

        public StubExecutionService(ExecutionMode mode, String eventPayload) {
            this.mode = mode;
            this.eventPayload = eventPayload;
        }

        @Override
        public ExecutionMode executionMode() {
            return mode;
        }

        @Override
        public Flux<ChatEvent> stream(ChatRequest request) {
            ChatEvent event = new ChatEvent(
                    request.taskId(),
                    1,
                    ChatEventType.TEXT_DELTA,
                    java.time.Instant.now(),
                    eventPayload,
                    false
            );
            return Flux.just(event);
        }

    }
}
