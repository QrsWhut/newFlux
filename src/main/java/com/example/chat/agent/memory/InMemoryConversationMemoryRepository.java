package com.example.chat.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 ConcurrentHashMap 的会话记忆仓储实现
 * <p>
 * 警告：该实现仅支持单机开发调试与自动化测试，不支持集群部署与物理持久化；
 * 生产环境部署前必须替换为 Redis 或数据库等分布式持久化存储实现。
 * </p>
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Repository
public class InMemoryConversationMemoryRepository implements ConversationMemoryRepository {

    private final ConcurrentHashMap<String, ConversationMemory> storage = new ConcurrentHashMap<>();

    @Override
    public Mono<ConversationMemory> findBySession(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return Mono.error(new IllegalArgumentException("userId 和 sessionId 不能为空"));
        }
        String key = buildKey(userId, sessionId);
        ConversationMemory memory = storage.get(key);
        if (memory == null) {
            memory = ConversationMemory.builder()
                    .summary("")
                    .recentTurns(new ArrayList<>())
                    .version(1L)
                    .build();
        }
        return Mono.just(copyMemory(memory));
    }

    @Override
    public Mono<Void> save(String userId, String sessionId, ConversationMemory memory) {
        if (userId == null || sessionId == null || memory == null) {
            return Mono.error(new IllegalArgumentException("存储入参不能为空"));
        }
        String key = buildKey(userId, sessionId);
        ConversationMemory copy = copyMemory(memory);
        copy.setVersion(copy.getVersion() + 1);
        storage.put(key, copy);
        return Mono.empty();
    }

    private String buildKey(String userId, String sessionId) {
        return userId.trim() + ":" + sessionId.trim();
    }

    private ConversationMemory copyMemory(ConversationMemory orig) {
        ArrayList<ConversationTurn> turnsCopy = new ArrayList<>();
        if (orig.getRecentTurns() != null) {
            for (ConversationTurn turn : orig.getRecentTurns()) {
                turnsCopy.add(ConversationTurn.builder()
                        .messages(turn.getMessages() == null
                                ? new ArrayList<>() : new ArrayList<>(turn.getMessages()))
                        .timestamp(turn.getTimestamp())
                        .build());
            }
        }
        return ConversationMemory.builder()
                .summary(orig.getSummary())
                .recentTurns(turnsCopy)
                .version(orig.getVersion())
                .build();
    }
}
