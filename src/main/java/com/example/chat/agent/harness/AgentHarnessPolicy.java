package com.example.chat.agent.harness;

import com.example.chat.agent.AgentTurnContext;
import com.example.chat.agent.context.ContextBudgetDecision;
import com.example.chat.agent.context.ContextEstimationInput;
import com.example.chat.agent.model.AgentToolDefinition;

import java.util.List;

/**
 * Agent 模型调用前的 Harness 治理策略。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface AgentHarnessPolicy {

    /**
     * 从当前 ReAct 步骤构造不可变预算输入。
     *
     * @param context 当前执行上下文
     * @param frozenToolDefinitions 本轮冻结的工具定义
     * @return 上下文预算输入
     */
    ContextEstimationInput createEstimationInput(
            AgentTurnContext context,
            List<AgentToolDefinition> frozenToolDefinitions);

    /**
     * 将上下文预算结果转换为 Harness 动作。
     *
     * @param budgetDecision 上下文预算结果
     * @return Harness 治理决策
     */
    AgentHarnessDecision decide(ContextBudgetDecision budgetDecision);
}
