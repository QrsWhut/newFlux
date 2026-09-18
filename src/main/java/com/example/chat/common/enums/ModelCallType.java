package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 模型调用用途。
 */
@Getter
public enum ModelCallType {

    /** Agent 主推理调用。 */
    MAIN(1),

    /** 会话摘要压缩调用。 */
    COMPRESSION(2);

    /** 持久化类型码。 */
    private final int code;

    /**
     * 创建模型调用类型。
     *
     * @param code 持久化类型码
     */
    ModelCallType(int code) {
        this.code = code;
    }
}
