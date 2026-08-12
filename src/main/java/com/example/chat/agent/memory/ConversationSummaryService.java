package com.example.chat.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 对淘汰的对话轮次进行摘要压缩的服务类
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Service
public class ConversationSummaryService {

    /**
     * 将已有的历史摘要与被淘汰的对话轮次合并生成新的结构化摘要
     *
     * @param existingSummary 已有摘要
     * @param evictedTurn     被淘汰的轮次
     * @return 新摘要 Mono
     */
    public Mono<String> summarize(String existingSummary, ConversationTurn evictedTurn) {
        if (evictedTurn == null) {
            return Mono.just(existingSummary != null ? existingSummary : "");
        }

        StringBuilder sb = new StringBuilder();
        if (existingSummary != null && !existingSummary.trim().isEmpty()) {
            sb.append(existingSummary.trim()).append("\n");
        }

        sb.append("【历史问答摘要】问：").append(evictedTurn.getUserQuestion())
                .append(" | 答：").append(truncateAnswer(evictedTurn.getAssistantAnswer()));
        return Mono.just(sb.toString());
    }

    private String truncateAnswer(String answer) {
        if (answer == null) return "";
        return answer.length() > 100 ? answer.substring(0, 100) + "..." : answer;
    }
}
