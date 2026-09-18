package com.example.chat.agent.prompt;

/**
 * 本地提示词用途。
 *
 * @author Codex
 * @since 2026-08-25
 */
public enum PromptPurpose {

    /** 金融 Agent 主系统提示词。 */
    FINANCIAL_AGENT_SYSTEM("prompts/financial-agent-system.md", "financial-agent-system-v1.0.0"),

    /** 会话摘要压缩提示词。 */
    CONVERSATION_SUMMARY("prompts/conversation-summary.md", "conversation-summary-v1.0.0");

    /** 类路径资源地址。 */
    private final String resourcePath;

    /** 提示词语义版本。 */
    private final String version;

    PromptPurpose(String resourcePath, String version) {
        this.resourcePath = resourcePath;
        this.version = version;
    }

    /**
     * 获取类路径资源地址。
     *
     * @return 类路径资源地址
     */
    public String getResourcePath() {
        return resourcePath;
    }

    /**
     * 获取提示词语义版本。
     *
     * @return 提示词语义版本
     */
    public String getVersion() {
        return version;
    }
}
