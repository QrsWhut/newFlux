package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentToolRegistry 注册表单元测试。
 */
public class AgentToolRegistryTest {

    @Test
    public void testRegistryLookup() {
        AgentToolRegistry registry = new AgentToolRegistry(
                List.of(new DummyTool("t1"), new DummyTool("t2")));

        assertTrue(registry.getTool("t1").isPresent());
        assertTrue(registry.getTool("t2").isPresent());
        assertFalse(registry.getTool("unknown").isPresent());
        assertEquals(2, registry.getTools().size());
    }

    @Test
    public void testDuplicateDefinitionNameThrowsStateError() {
        assertThrows(IllegalStateException.class, () -> new AgentToolRegistry(
                List.of(new DummyTool("dup"), new DummyTool("dup"))));
    }

    private static class DummyTool implements AgentTool<String> {

        private final AgentToolDefinition definition;

        private DummyTool(String name) {
            definition = AgentToolDefinition.builder()
                    .type("function")
                    .function(AgentToolDefinition.FunctionDefinition.builder()
                            .name(name)
                            .build())
                    .build();
        }

        @Override
        public AgentToolDefinition definition() {
            return definition;
        }

        @Override
        public Class<String> inputType() {
            return String.class;
        }

        @Override
        public AgentToolMetadata metadata() {
            return AgentToolMetadata.authenticatedReadOnly();
        }

        @Override
        public Mono<AgentToolResult> call(String input, AgentToolContext context) {
            return Mono.just(AgentToolResult.success("ok", null));
        }
    }
}
