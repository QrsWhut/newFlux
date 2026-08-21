package com.example.chat.agent.tool;

/**
 * Agent 工具标准错误码。
 */
public enum AgentToolErrorCode {

    /** 工具不存在。 */
    TOOL_NOT_FOUND,
    /** 工具调用无权限。 */
    TOOL_FORBIDDEN,
    /** 参数 JSON 无法解析。 */
    INVALID_ARGUMENTS,
    /** 参数未通过业务校验。 */
    VALIDATION_FAILED,
    /** 工具执行超时。 */
    TOOL_TIMEOUT,
    /** 下游能力暂不可用。 */
    DOWNSTREAM_UNAVAILABLE,
    /** 工具未返回有效结果。 */
    EMPTY_RESULT
}
