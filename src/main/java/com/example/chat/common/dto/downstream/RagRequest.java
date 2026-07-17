package com.example.chat.common.dto.downstream;

/**
 * RAG 检索服务请求参数 DTO
 *
 * @param question  改写后的增强问句
 * @param sessionId 当前会话 ID
 * @param maxToken  允许返回的最大 Token 数
 * @param topK      关联文档召回 TopK 数量
 * @author Antigravity
 * @since 2026-07-17
 */
public record RagRequest(
        String question,
        String sessionId,
        int maxToken,
        int topK) {
}
