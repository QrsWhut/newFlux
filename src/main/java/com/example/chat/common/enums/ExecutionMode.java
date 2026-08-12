package com.example.chat.common.enums;

/**
 * 对话服务执行模式枚举
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public enum ExecutionMode {

    /**
     * 固定工作流模式，按照预定义的 Stage 节点顺序链式执行
     */
    WORKFLOW,

    /**
     * ReAct Agent 模式，由 LLM 根据多轮上下文自主选择与调用工具
     */
    AGENT;

    /**
     * 从字符串不区分大小写解析 ExecutionMode，解析失败返回 null
     *
     * @param value 输入字符串
     * @return 解析后的 ExecutionMode，若无法解析则返回 null
     */
    public static ExecutionMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        for (ExecutionMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
