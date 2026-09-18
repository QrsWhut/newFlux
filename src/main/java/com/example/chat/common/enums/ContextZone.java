package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 模型输入上下文区域。
 */
@Getter
public enum ContextZone {

    /** 输入处于安全阈值以内。 */
    SAFE(1),

    /** 输入超过安全阈值但没有超过硬限制。 */
    RISK(2),

    /** 输入超过硬限制。 */
    REJECT(3);

    /** 持久化区域码。 */
    private final int code;

    /**
     * 创建上下文区域。
     *
     * @param code 持久化区域码
     */
    ContextZone(int code) {
        this.code = code;
    }
}
