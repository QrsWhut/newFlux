package com.example.chat.agent.tool;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentToolRegistry 注册表单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class AgentToolRegistryTest {

    @Test
    public void testRegistryLookupAndDefinitions() {
        AgentTool tool1 = new DummyTool("t1");
        AgentTool tool2 = new DummyTool("t2");

        AgentToolRegistry registry = new AgentToolRegistry(List.of(tool1, tool2));

        assertTrue(registry.getTool("t1").isPresent());
        assertTrue(registry.getTool("t2").isPresent());
        assertFalse(registry.getTool("unknown").isPresent());
        assertEquals(2, registry.getDefinitions().size());
    }

    @Test
    public void testDuplicateToolNameThrowsStateError() {
        AgentTool t1 = new DummyTool("dup");
        AgentTool t2 = new DummyTool("dup");

        assertThrows(IllegalStateException.class, () -> new AgentToolRegistry(List.of(t1, t2)));
    }

    private static class DummyTool implements AgentTool {
        private final String name;

        public DummyTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public com.example.chat.agent.model.AgentToolDefinition definition() {
            return com.example.chat.agent.model.AgentToolDefinition.builder()
                    .function(com.example.chat.agent.model.AgentToolDefinition.FunctionDefinition.builder().name(name).build())
                    .build();
        }

        @Override
        public reactor.core.publisher.Mono<AgentToolResult> execute(String args, String sessionId) {
            return reactor.core.publisher.Mono.just(AgentToolResult.success(name, "ok", null));
        }
    }
}
