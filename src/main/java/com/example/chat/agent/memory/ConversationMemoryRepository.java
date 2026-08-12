package com.example.chat.agent.memory;

import reactor.core.publisher.Mono;

/**
 * 会话记忆仓储接口
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public interface ConversationMemoryRepository {

    /**
     * 根据 userId 和 sessionId 获取会话记忆
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return 会话记忆 Mono
     */
    Mono<ConversationMemory> findBySession(String userId, String sessionId);

    /**
     * 保存或更新会话记忆
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param memory    会话记忆实体
     * @return Void Mono
     */
    Mono<Void> save(String userId, String sessionId, ConversationMemory memory);
}
