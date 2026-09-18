package com.example.chat.agent.context;

import com.example.chat.common.enums.ContextErrorCode;
import com.example.chat.common.enums.ContextZone;
import com.example.chat.config.AgentMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 上下文预算服务边界测试。
 */
public class ContextBudgetServiceTest {

    /** 模拟 Token 估算器。 */
    private ContextTokenEstimator estimator;
    /** 被测预算服务。 */
    private ContextBudgetService budgetService;
    /** 固定上下文输入。 */
    private ContextEstimationInput input;

    /**
     * 创建固定窗口与模拟估算器。
     */
    @BeforeEach
    public void setUp() {
        estimator = Mockito.mock(ContextTokenEstimator.class);
        AgentMemoryProperties properties = new AgentMemoryProperties(
                AgentMemoryProperties.STORE_TYPE_LOCAL,
                new AgentMemoryProperties.BudgetProperties(
                        1_000,
                        100,
                        new BigDecimal("0.70"),
                        new BigDecimal("0.45"),
                        new BigDecimal("0.50"),
                        BigDecimal.ONE),
                null,
                null);
        budgetService = new ContextBudgetService(estimator, properties);
        input = new ContextEstimationInput(
                "provider", "model", "", List.of(), "", List.of(),
                "", "", List.of(), 0);
        Mockito.when(estimator.fingerprint(input)).thenReturn("fingerprint");
    }

    /**
     * 验证恰好等于安全阈值时允许安全请求。
     */
    @Test
    public void testEqualSafeLimitIsSafe() {
        Mockito.when(estimator.estimate(input)).thenReturn(breakdownWithHistory(630));

        ContextBudgetDecision decision = budgetService.evaluate(input);

        assertEquals(ContextZone.SAFE, decision.zone());
        assertEquals(630, decision.safeInputLimit());
    }

    /**
     * 验证恰好等于硬限制时属于风险区域。
     */
    @Test
    public void testEqualHardLimitIsRisk() {
        Mockito.when(estimator.estimate(input)).thenReturn(breakdownWithHistory(900));

        ContextBudgetDecision decision = budgetService.evaluate(input);

        assertEquals(ContextZone.RISK, decision.zone());
        assertEquals(900, decision.hardInputLimit());
    }

    /**
     * 验证超过硬限制的历史被分类为历史过长。
     */
    @Test
    public void testOverHardLimitRejectsHistory() {
        Mockito.when(estimator.estimate(input)).thenReturn(breakdownWithHistory(901));

        ContextBudgetDecision decision = budgetService.evaluate(input);

        assertEquals(ContextZone.REJECT, decision.zone());
        assertEquals(ContextErrorCode.HISTORY_TOO_LONG, decision.errorCode());
    }

    private ContextTokenBreakdown breakdownWithHistory(int historyTokens) {
        return new ContextTokenBreakdown(0, 0, 0, historyTokens, 0, 0, 0, 0);
    }
}
