package com.example.chat.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆包含结构化摘要与最多三轮完整问答
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemory {

    /**
     * 淘汰轮次的结构化中文字段摘要
     */
    @Builder.Default
    private String summary = "";

    /**
     * 最近三轮完整问答
     */
    @Builder.Default
    private List<ConversationTurn> recentTurns = new ArrayList<>();

    @Builder.Default
    private long version = 1L;
}
