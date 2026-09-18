package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 会话上下文压缩等级。
 */
@Getter
public enum CompressionLevel {

    /** 未执行压缩。 */
    NONE(0),

    /** 已执行普通压缩。 */
    NORMAL(1),

    /** 已执行深度压缩。 */
    DEEP(2);

    /** 持久化等级码。 */
    private final int code;

    /**
     * 创建压缩等级。
     *
     * @param code 持久化等级码
     */
    CompressionLevel(int code) {
        this.code = code;
    }
}
