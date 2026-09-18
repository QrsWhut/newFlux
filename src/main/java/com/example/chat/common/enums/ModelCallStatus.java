package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 模型调用状态。
 */
@Getter
public enum ModelCallStatus {

    /** 模型请求已经登记并等待结果。 */
    REQUESTING(1),

    /** 模型调用成功。 */
    SUCCESS(2),

    /** 模型调用失败。 */
    FAILED(3),

    /** 模型调用超时。 */
    TIMEOUT(4),

    /** 模型调用被取消。 */
    CANCELLED(5);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建模型调用状态。
     *
     * @param code 持久化状态码
     */
    ModelCallStatus(int code) {
        this.code = code;
    }
}
