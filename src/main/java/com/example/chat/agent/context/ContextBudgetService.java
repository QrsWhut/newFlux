package com.example.chat.agent.context;

import com.example.chat.common.enums.ContextErrorCode;
import com.example.chat.common.enums.ContextZone;
import com.example.chat.config.AgentMemoryProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 依据模型窗口、安全水位和压缩水位生成上下文预算决策。
 */
@Service
public class ContextBudgetService {

    /** 上下文 Token 估算器。 */
    private final ContextTokenEstimator tokenEstimator;
    /** Agent 记忆配置。 */
    private final AgentMemoryProperties memoryProperties;

    /**
     * 创建上下文预算服务。
     *
     * @param tokenEstimator Token 估算器
     * @param memoryProperties Agent 记忆配置
     */
    public ContextBudgetService(
            ContextTokenEstimator tokenEstimator,
            AgentMemoryProperties memoryProperties) {
        this.tokenEstimator = tokenEstimator;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 使用默认模型窗口生成预算决策。
     *
     * @param input 不可变上下文输入
     * @return 上下文预算决策
     */
    public ContextBudgetDecision evaluate(ContextEstimationInput input) {
        AgentMemoryProperties.BudgetProperties budgetProperties = memoryProperties.budget();
        return evaluate(input, budgetProperties.contextWindowTokens(), budgetProperties.maxOutputTokens());
    }

    /**
     * 使用路由选定的模型窗口生成预算决策。
     *
     * @param input 不可变上下文输入
     * @param contextWindowTokens 模型上下文窗口
     * @param maxOutputTokens 本次最大输出 Token
     * @return 上下文预算决策
     */
    public ContextBudgetDecision evaluate(
            ContextEstimationInput input,
            int contextWindowTokens,
            int maxOutputTokens) {
        validateLimits(contextWindowTokens, maxOutputTokens);
        ContextTokenBreakdown breakdown = tokenEstimator.estimate(input);
        int hardInputLimit = contextWindowTokens - maxOutputTokens;
        AgentMemoryProperties.BudgetProperties budgetProperties = memoryProperties.budget();
        int safeInputLimit = multiplyAndFloor(hardInputLimit, budgetProperties.safeInputRatio());
        int compressionTargetLimit = multiplyAndFloor(
                hardInputLimit, budgetProperties.compressionTargetRatio());
        int estimatedInputTokens = breakdown.totalTokens();
        ContextZone zone = resolveZone(estimatedInputTokens, safeInputLimit, hardInputLimit);
        ContextErrorCode errorCode = zone == ContextZone.REJECT
                ? classifyOverflow(breakdown, hardInputLimit) : null;
        return new ContextBudgetDecision(
                breakdown,
                estimatedInputTokens,
                hardInputLimit,
                safeInputLimit,
                compressionTargetLimit,
                hardInputLimit - safeInputLimit,
                breakdown.minimumRequiredTokens(),
                zone,
                errorCode,
                tokenEstimator.fingerprint(input));
    }

    private void validateLimits(int contextWindowTokens, int maxOutputTokens) {
        if (contextWindowTokens <= 0 || maxOutputTokens <= 0) {
            throw new IllegalArgumentException("上下文窗口和最大输出 Token 必须大于 0");
        }
        if (maxOutputTokens >= contextWindowTokens) {
            throw new IllegalArgumentException("最大输出 Token 必须小于上下文窗口");
        }
    }

    private int multiplyAndFloor(int value, BigDecimal ratio) {
        return BigDecimal.valueOf(value)
                .multiply(ratio)
                .setScale(0, RoundingMode.FLOOR)
                .intValueExact();
    }

    private ContextZone resolveZone(
            int estimatedInputTokens,
            int safeInputLimit,
            int hardInputLimit) {
        if (estimatedInputTokens <= safeInputLimit) {
            return ContextZone.SAFE;
        }
        if (estimatedInputTokens <= hardInputLimit) {
            return ContextZone.RISK;
        }
        return ContextZone.REJECT;
    }

    private ContextErrorCode classifyOverflow(
            ContextTokenBreakdown breakdown,
            int hardInputLimit) {
        if (breakdown.staticContextTokens() + breakdown.protocolOverheadTokens() > hardInputLimit) {
            return ContextErrorCode.STATIC_CONTEXT_TOO_LARGE;
        }
        long currentInputRequiredTokens = (long) breakdown.staticContextTokens()
                + breakdown.currentUserTokens() + breakdown.pageContextTokens()
                + breakdown.protocolOverheadTokens();
        if (currentInputRequiredTokens > hardInputLimit) {
            return ContextErrorCode.CURRENT_INPUT_TOO_LARGE;
        }
        if (breakdown.minimumRequiredTokens() > hardInputLimit
                && breakdown.currentTurnToolTokens() > 0) {
            return ContextErrorCode.TOOL_RESULT_TOO_LARGE;
        }
        return ContextErrorCode.HISTORY_TOO_LONG;
    }
}
