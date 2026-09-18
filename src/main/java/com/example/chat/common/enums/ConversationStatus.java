package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 会话生命周期状态。
 */
@Getter
public enum ConversationStatus {

    /** 活跃会话。 */
    ACTIVE(1),

    /** 已关闭会话。 */
    CLOSED(2),

    /** 已归档会话。 */
    ARCHIVED(3);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建会话状态。
     *
     * @param code 持久化状态码
     */
    ConversationStatus(int code) {
        this.code = code;
    }
}
