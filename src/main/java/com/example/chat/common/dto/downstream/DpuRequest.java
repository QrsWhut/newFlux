package com.example.chat.common.dto.downstream;

/**
 * DPU 检索服务请求参数 DTO
 *
 * @param question     改写后的增强问句
 * @param useAiService 是否启用智能分析服务
 * @param sessionId    当前会话 ID
 * @author Antigravity
 * @since 2026-07-17
 */
public record DpuRequest(
        String question,
        boolean useAiService,
        String sessionId) {
}
