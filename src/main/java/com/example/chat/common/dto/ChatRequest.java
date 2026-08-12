package com.example.chat.common.dto;

import com.example.chat.common.enums.ExecutionMode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 对话请求 DTO
 * 该对象描述业务层的核心输入，脱离 HTTP 和特定通讯组件
 *
 * @param taskId        任务ID（由客户端或前端传入，作为链路唯一标识）
 * @param sessionId     会话ID（对应用户的一轮或多轮对话会话）
 * @param userId        用户ID
 * @param question      用户输入的问句
 * @param history       历史消息列表
 * @param attributes    额外携带的业务属性（如 pageData 等）
 * @param executionMode 执行模式（WORKFLOW 或 AGENT）
 * @author Antigravity
 * @since 2026-07-17
 */
public record ChatRequest(
        String taskId,
        String sessionId,
        String userId,
        String question,
        List<ChatMessage> history,
        Map<String, Object> attributes,
        ExecutionMode executionMode) {

    public ChatRequest {
        if (history == null) {
            history = Collections.emptyList();
        }
        if (executionMode == null) {
            executionMode = ExecutionMode.WORKFLOW;
        }
    }

    /**
     * 兼容旧版不带 executionMode 的构造函数，默认模式为 WORKFLOW
     */
    public ChatRequest(String taskId, String sessionId, String userId, String question, List<ChatMessage> history, Map<String, Object> attributes) {
        this(taskId, sessionId, userId, question, history, attributes, ExecutionMode.WORKFLOW);
    }
}

