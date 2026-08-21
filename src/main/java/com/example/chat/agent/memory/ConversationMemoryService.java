package com.example.chat.agent.memory;

import com.example.chat.agent.model.AgentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理三轮窗口与摘要式服务端会话记忆的核心服务
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Service
public class ConversationMemoryService {

    private final ConversationMemoryRepository repository;
    private final ConversationSummaryService summaryService;

    public ConversationMemoryService(
            ConversationMemoryRepository repository,
            ConversationSummaryService summaryService) {
        this.repository = repository;
        this.summaryService = summaryService;
    }

    /**
     * 获取指定 session 的内存记录
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return ConversationMemory Mono
     */
    public Mono<ConversationMemory> getMemory(String userId, String sessionId) {
        if (userId == null || userId.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("userId 与 sessionId 均不能为空"));
        }
        return repository.findBySession(userId, sessionId);
    }

    /**
     * 追加一轮有序消息，并自动维持三轮窗口与摘要更新。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param messages 有序消息列表
     * @return 完成信号
     */
    public Mono<Void> appendTurn(String userId, String sessionId, List<AgentMessage> messages) {
        if (userId == null || userId.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("userId 与 sessionId 均不能为空"));
        }
        if (messages == null || messages.isEmpty()) {
            return Mono.error(new IllegalArgumentException("messages 不能为空"));
        }

        return repository.findBySession(userId, sessionId)
                .flatMap(memory -> {
                    ConversationTurn newTurn = ConversationTurn.builder()
                            .messages(List.copyOf(messages))
                            .timestamp(Instant.now())
                            .build();

                    var recentTurns = new ArrayList<>(memory.getRecentTurns());
                    recentTurns.add(newTurn);

                    if (recentTurns.size() > 3) {
                        ConversationTurn evictedTurn = recentTurns.remove(0);
                        return summaryService.summarize(memory.getSummary(), evictedTurn)
                                .flatMap(newSummary -> {
                                    memory.setSummary(newSummary);
                                    memory.setRecentTurns(recentTurns);
                                    return repository.save(userId, sessionId, memory);
                                });
                    } else {
                        memory.setRecentTurns(recentTurns);
                        return repository.save(userId, sessionId, memory);
                    }
                });
    }

    /**
     * 以用户问题和助手回答追加简单轮次。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param userQuestion 用户问题
     * @param assistantAnswer 助手回答
     * @return 完成信号
     * @deprecated 请使用有序消息列表追加完整轨迹
     */
    @Deprecated
    public Mono<Void> appendTurn(
            String userId, String sessionId, String userQuestion, String assistantAnswer) {
        if (userQuestion == null || assistantAnswer == null) {
            return Mono.error(new IllegalArgumentException("userQuestion 与 assistantAnswer 不能为空"));
        }
        return appendTurn(userId, sessionId, List.of(
                AgentMessage.user(userQuestion), AgentMessage.assistant(assistantAnswer)));
    }
}
