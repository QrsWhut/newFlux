package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 模型调用结果应用状态。
 */
@Getter
public enum ModelCallApplyStatus {

    /** 主模型调用不需要发布派生结果。 */
    NOT_APPLICABLE(1),

    /** 压缩结果等待发布。 */
    PENDING(2),

    /** 压缩结果已经发布。 */
    APPLIED(3),

    /** 压缩结果因冲突或失效被丢弃。 */
    DISCARDED(4);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建结果应用状态。
     *
     * @param code 持久化状态码
     */
    ModelCallApplyStatus(int code) {
        this.code = code;
    }
}
