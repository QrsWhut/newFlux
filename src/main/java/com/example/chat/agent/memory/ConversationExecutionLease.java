package com.example.chat.agent.memory;

import java.time.LocalDateTime;

/**
 * 会话执行租约不可变值对象。
 *
 * @param conversationId 会话主键
 * @param userId 用户标识
 * @param sessionId 会话标识
 * @param owner 租约持有者
 * @param executionEpoch 隔离栅栏版本
 * @param expireTime 租约过期时间
 */
public record ConversationExecutionLease(
        Long conversationId,
        String userId,
        String sessionId,
        String owner,
        Long executionEpoch,
        LocalDateTime expireTime) {

    /**
     * 校验租约必要字段。
     */
    public ConversationExecutionLease {
        if (conversationId == null || userId == null || userId.isBlank()
                || sessionId == null || sessionId.isBlank() || owner == null || owner.isBlank()
                || executionEpoch == null || executionEpoch <= 0L || expireTime == null) {
            throw new IllegalArgumentException("会话执行租约字段不完整");
        }
    }
}
