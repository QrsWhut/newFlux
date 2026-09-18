package com.example.chat.dal.dao;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 会话记忆 DDL 静态合约测试。
 */
public class ConversationSchemaContractTest {

    /** DDL 类路径。 */
    private static final String SCHEMA_RESOURCE =
            "db/schema/agent_conversation_memory.sql";

    /**
     * 验证 fencing、attempt、恢复索引和无外键约束。
     *
     * @throws IOException 读取测试资源失败时抛出
     */
    @Test
    public void testSchemaContainsRequiredConsistencyFields() throws IOException {
        String schema = readSchema().toLowerCase(java.util.Locale.ROOT);

        assertTrue(schema.contains("execution_epoch"));
        assertTrue(schema.contains("idx_execution_expire_time"));
        assertTrue(schema.contains("attempt_no"));
        assertTrue(schema.contains("uk_turn_call_attempt"));
        assertTrue(schema.contains("provider_code"));
        assertTrue(schema.contains("system_prompt_hash"));
        assertTrue(schema.contains("tool_schema_hash"));
        assertFalse(schema.contains("foreign key"));
    }

    private String readSchema() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("未找到 Agent 会话记忆 DDL");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
