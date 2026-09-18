package com.example.chat.common.enums;

import lombok.Getter;

/**
 * 上下文预算错误码。
 */
@Getter
public enum ContextErrorCode {

    /** 当前输入、附件或页面数据本身过大。 */
    CURRENT_INPUT_TOO_LARGE("CURRENT_INPUT_TOO_LARGE"),

    /** 历史在压缩后仍然过长。 */
    HISTORY_TOO_LONG("HISTORY_TOO_LONG"),

    /** 系统提示词或必要工具定义过大。 */
    STATIC_CONTEXT_TOO_LARGE("STATIC_CONTEXT_TOO_LARGE"),

    /** 当前轮工具观察结果过大。 */
    TOOL_RESULT_TOO_LARGE("TOOL_RESULT_TOO_LARGE"),

    /** 估算可容纳但供应方实际拒绝。 */
    ESTIMATION_INACCURATE("ESTIMATION_INACCURATE"),

    /** 摘要压缩调用或结果校验失败。 */
    COMPRESSION_FAILED("COMPRESSION_FAILED"),

    /** 会话上下文发生并发更新。 */
    CONCURRENT_CONTEXT_UPDATE("CONCURRENT_CONTEXT_UPDATE"),

    /** 深度压缩后仍超过硬限制。 */
    CONTEXT_LIMIT_EXCEEDED("CONTEXT_LIMIT_EXCEEDED");

    /** 对外稳定错误码。 */
    private final String code;

    /**
     * 创建上下文错误码。
     *
     * @param code 对外稳定错误码
     */
    ContextErrorCode(String code) {
        this.code = code;
    }
}
