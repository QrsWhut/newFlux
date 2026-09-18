package com.example.chat.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型调用 Token 用量。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentModelUsage {

    /** 输入 Token 数。 */
    private Long inputTokens;

    /** 输出 Token 数。 */
    private Long outputTokens;

    /** 总 Token 数。 */
    private Long totalTokens;

    /** 命中缓存的输入 Token 数。 */
    private Long cachedInputTokens;

    /** 推理 Token 数。 */
    private Long reasoningTokens;
}
