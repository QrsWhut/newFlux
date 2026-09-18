package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 会话轮次状态。
 */
@Getter
public enum ConversationTurnStatus {

    /** 轮次正在执行。 */
    PROCESSING(1),

    /** 轮次成功完成。 */
    SUCCESS(2),

    /** 轮次执行失败。 */
    FAILED(3),

    /** 轮次被取消。 */
    CANCELLED(4);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建轮次状态。
     *
     * @param code 持久化状态码
     */
    ConversationTurnStatus(int code) {
        this.code = code;
    }
}
