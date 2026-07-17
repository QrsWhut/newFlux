package com.example.chat.common.dto;

/**
 * 单条历史对话消息
 *
 * @param role    角色，例如 user, assistant
 * @param content 消息文本内容
 * @author Antigravity
 * @since 2026-07-17
 */
public record ChatMessage(String role, String content) {
}
