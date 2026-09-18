package com.example.chat.agent.prompt;

/**
 * 不可变提示词快照。
 *
 * @param purpose 提示词用途
 * @param version 提示词语义版本
 * @param contentHash 提示词内容哈希
 * @param content 提示词正文
 * @author Codex
 * @since 2026-08-25
 */
public record PromptSnapshot(
        PromptPurpose purpose,
        String version,
        String contentHash,
        String content) {
}
