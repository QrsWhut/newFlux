package com.example.chat.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 工具结构化错误。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolError {

    /** 标准错误码。 */
    private AgentToolErrorCode code;
    /** 面向模型和调用方的安全错误说明。 */
    private String message;
    /** 是否建议模型稍后重试。 */
    private boolean retryable;

    /**
     * 创建标准工具错误。
     *
     * @param code 错误码
     * @param message 安全错误说明
     * @param retryable 是否可重试
     * @return 工具错误
     */
    public static AgentToolError of(AgentToolErrorCode code, String message, boolean retryable) {
        return AgentToolError.builder()
                .code(code)
                .message(message)
                .retryable(retryable)
                .build();
    }
}
