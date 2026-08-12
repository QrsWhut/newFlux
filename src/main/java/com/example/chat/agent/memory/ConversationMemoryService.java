package com.example.chat.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

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

    public ConversationMemoryService(ConversationMemoryRepository repository, ConversationSummaryService summaryService) {
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
     * 追加一轮新的完整问答，并自动维持 3 轮窗口与摘要更新
     *
     * @param userId          用户 ID
     * @param sessionId       会话 ID
     * @param userQuestion    用户提问
     * @param assistantAnswer 助手最终回答
     * @return Void Mono
     */
    public Mono<Void> appendTurn(String userId, String sessionId, String userQuestion, String assistantAnswer) {
        if (userId == null || userId.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("userId 与 sessionId 均不能为空"));
        }
        if (userQuestion == null || assistantAnswer == null) {
            return Mono.error(new IllegalArgumentException("userQuestion 与 assistantAnswer 不能为空"));
        }

        return repository.findBySession(userId, sessionId)
                .flatMap(memory -> {
                    ConversationTurn newTurn = ConversationTurn.builder()
                            .userQuestion(userQuestion)
                            .assistantAnswer(assistantAnswer)
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
}
