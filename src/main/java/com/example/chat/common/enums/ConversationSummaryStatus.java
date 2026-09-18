package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 会话摘要状态。
 */
@Getter
public enum ConversationSummaryStatus {

    /** 摘要已经发布。 */
    PUBLISHED(1),

    /** 摘要已经被更新版本替代。 */
    SUPERSEDED(2),

    /** 摘要校验失败或被判定无效。 */
    INVALID(3);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建摘要状态。
     *
     * @param code 持久化状态码
     */
    ConversationSummaryStatus(int code) {
        this.code = code;
    }
}
