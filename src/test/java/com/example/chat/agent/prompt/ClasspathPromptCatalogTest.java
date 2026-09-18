package com.example.chat.agent.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 类路径提示词目录测试。
 *
 * @author Codex
 * @since 2026-08-25
 */
class ClasspathPromptCatalogTest {

    /**
     * 验证全部提示词均可按 UTF-8 加载并生成稳定哈希。
     */
    @Test
    void shouldLoadVersionedPromptsWithStableHash() {
        ClasspathPromptCatalog firstCatalog = new ClasspathPromptCatalog();
        ClasspathPromptCatalog secondCatalog = new ClasspathPromptCatalog();

        PromptSnapshot systemPrompt = firstCatalog.getPrompt(PromptPurpose.FINANCIAL_AGENT_SYSTEM);
        PromptSnapshot summaryPrompt = firstCatalog.getPrompt(PromptPurpose.CONVERSATION_SUMMARY);

        assertFalse(systemPrompt.content().isBlank());
        assertFalse(summaryPrompt.content().isBlank());
        assertEquals(64, systemPrompt.contentHash().length());
        assertEquals(
                systemPrompt.contentHash(),
                secondCatalog.getPrompt(PromptPurpose.FINANCIAL_AGENT_SYSTEM).contentHash());
        assertNotEquals(systemPrompt.contentHash(), summaryPrompt.contentHash());
    }

    /**
     * 验证空用途会被明确拒绝。
     */
    @Test
    void shouldRejectNullPurpose() {
        ClasspathPromptCatalog catalog = new ClasspathPromptCatalog();

        assertThrows(IllegalArgumentException.class, () -> catalog.getPrompt(null));
    }
}
