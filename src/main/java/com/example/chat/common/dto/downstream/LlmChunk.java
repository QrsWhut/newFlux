package com.example.chat.common.dto.downstream;

/**
 * 对应大模型流式输出分片 DTO
 *
 * @param text             正文内容分片
 * @param reasoningContent 思考过程内容分片
 * @author Antigravity
 * @since 2026-07-17
 */
public record LlmChunk(String text, String reasoningContent) {
}
