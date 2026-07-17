package com.example.chat.common.dto.downstream;

import java.util.Map;

/**
 * 大模型请求参数封装 DTO
 *
 * @param promptId     Prompt 模板 ID
 * @param stream       是否以 SSE 流式输出
 * @param promptParams Prompt 渲染所用的入参 Key-Value
 * @author Antigravity
 * @since 2026-07-17
 */
public record LlmRequest(
        String promptId,
        boolean stream,
        Map<String, Object> promptParams) {
}
