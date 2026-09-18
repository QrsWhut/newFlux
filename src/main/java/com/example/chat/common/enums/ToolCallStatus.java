package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 工具调用状态。
 */
@Getter
public enum ToolCallStatus {

    /** 工具调用等待执行。 */
    PENDING(1),

    /** 工具调用正在执行。 */
    RUNNING(2),

    /** 工具调用成功。 */
    SUCCESS(3),

    /** 工具调用失败。 */
    FAILED(4),

    /** 工具调用超时。 */
    TIMEOUT(5),

    /** 工具调用被取消。 */
    CANCELLED(6),

    /** 工具调用因重复或策略原因被跳过。 */
    SKIPPED(7);

    /** 持久化状态码。 */
    private final int code;

    /**
     * 创建工具调用状态。
     *
     * @param code 持久化状态码
     */
    ToolCallStatus(int code) {
        this.code = code;
    }
}
