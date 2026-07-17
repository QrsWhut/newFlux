package com.example.chat.common.dto.downstream;

import java.util.List;
import java.util.Map;

/**
 * 问句改写请求参数 DTO
 *
 * @param messages 历史消息
 * @param userId   用户唯一ID
 * @param model    改写大模型名称
 * @param version  改写模型版本
 * @author Antigravity
 * @since 2026-07-17
 */
public record MultiRewriteParam(
        List<Map<String, String>> messages,
        String userId,
        String model,
        String version) {
}
