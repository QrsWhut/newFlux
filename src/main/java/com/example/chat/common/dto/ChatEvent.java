package com.example.chat.common.dto;

import com.example.chat.common.enums.ChatEventType;
import java.time.Instant;

/**
 * 内部统一流式业务事件
 * 该对象可被 HTTP SSE、A2A 或消息队列等通信层复用
 *
 * @param taskId    任务唯一标识
 * @param sequence  事件自增序号（从 1 开始）
 * @param type      事件类型
 * @param timestamp 事件发生的时间戳
 * @param payload   事件负载数据（如文本分片、UI 节点、错误信息等）
 * @param terminal  是否为终止事件
 * @author Antigravity
 * @since 2026-07-17
 */
public record ChatEvent(
        String taskId,
        long sequence,
        ChatEventType type,
        Instant timestamp,
        Object payload,
        boolean terminal) {

    /**
     * 快速构建文本增量事件
     */
    public static ChatEvent text(String taskId, long sequence, String text, String head) {
        return new ChatEvent(taskId, sequence, ChatEventType.TEXT_DELTA,
                Instant.now(), new TextDelta(text, head), false);
    }

    /**
     * 快速构建文本思考增量事件
     */
    public static ChatEvent thinking(String taskId, long sequence, String reasoningText, String head) {
        return new ChatEvent(taskId, sequence, ChatEventType.TEXT_DELTA,
                Instant.now(), new TextDelta("", reasoningText, head), false);
    }

    /**
     * 快速构建状态变更事件
     */
    public static ChatEvent status(String taskId, long sequence, String statusDescription) {
        return new ChatEvent(taskId, sequence, ChatEventType.STATUS,
                Instant.now(), new StatusPayload(statusDescription), false);
    }

    /**
     * 快速构建 UI 卡片更新事件
     */
    public static ChatEvent ui(String taskId, long sequence, UiNode uiNode) {
        return new ChatEvent(taskId, sequence, ChatEventType.UI_UPDATE,
                Instant.now(), uiNode, false);
    }

    /**
     * 快速构建正常完成事件
     */
    public static ChatEvent complete(String taskId, long sequence) {
        return new ChatEvent(taskId, sequence, ChatEventType.COMPLETE,
                Instant.now(), null, true);
    }

    /**
     * 快速构建取消事件
     */
    public static ChatEvent cancelled(String taskId, long sequence) {
        return new ChatEvent(taskId, sequence, ChatEventType.CANCELLED,
                Instant.now(), null, true);
    }

    /**
     * 快速构建错误事件
     */
    public static ChatEvent error(String taskId, long sequence, String errorCode, String errorMessage) {
        return new ChatEvent(taskId, sequence, ChatEventType.ERROR,
                Instant.now(), new ErrorPayload(errorCode, errorMessage), true);
    }

    /**
     * 文本数据负载
     */
    public record TextDelta(String content, String reasoningContent, String head) {
        public TextDelta(String content, String head) {
            this(content, "", head);
        }
    }

    /**
     * 状态负载
     */
    public record StatusPayload(String message) {
    }

    /**
     * 错误负载
     */
    public record ErrorPayload(String errorCode, String errorMessage) {
    }
}
