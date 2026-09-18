package com.example.chat.agent.prompt;

/**
 * 提示词目录接口。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface PromptCatalog {

    /**
     * 按用途获取不可变提示词快照。
     *
     * @param purpose 提示词用途
     * @return 提示词快照
     * @throws IllegalArgumentException 当用途为空时抛出
     * @throws IllegalStateException 当对应提示词不存在时抛出
     */
    PromptSnapshot getPrompt(PromptPurpose purpose);
}
