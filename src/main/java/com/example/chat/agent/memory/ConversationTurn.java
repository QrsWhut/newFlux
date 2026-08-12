package com.example.chat.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 单轮完整问答记录 (一轮包含用户提问与助手最终回答，不含中间工具调用轨迹)
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurn {

    private String userQuestion;
    private String assistantAnswer;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
