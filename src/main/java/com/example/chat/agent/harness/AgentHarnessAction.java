package com.example.chat.agent.harness;

/**
 * Agent Harness 对单次模型调用的治理动作。
 *
 * @author Codex
 * @since 2026-08-25
 */
public enum AgentHarnessAction {

    /** 允许发起模型调用。 */
    ALLOW,

    /** 拒绝发起模型调用并返回结构化错误。 */
    REJECT
}
