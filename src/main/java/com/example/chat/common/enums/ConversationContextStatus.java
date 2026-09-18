package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 会话上下文治理状态。
 */
@Getter
public enum ConversationContextStatus {

    /** 上下文处于正常水位。 */
    NORMAL(1),

    /** 上下文接近模型限制。 */
    NEAR_LIMIT(2),

    /** 建议用户完成当前问题后开启新会话。 */
    RESET_RECOMMENDED(3),

    /** 上下文已经超过可用限制。 */
    EXCEEDED(4);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建上下文治理状态。
     *
     * @param code 持久化状态码
     */
    ConversationContextStatus(int code) {
        this.code = code;
    }
}
