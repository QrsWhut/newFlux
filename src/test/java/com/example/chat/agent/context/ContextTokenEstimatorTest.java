package com.example.chat.agent.context;

import com.example.chat.config.AgentMemoryProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 上下文 Token 估算器测试。
 */
public class ContextTokenEstimatorTest {

    /**
     * 验证 UTF-8 字节、校准系数与协议开销共同参与估算。
     */
    @Test
    public void testEstimateByUtf8BytesAndProtocolOverhead() {
        ContextTokenEstimator estimator = new ContextTokenEstimator(memoryProperties());
        ContextEstimationInput input = new ContextEstimationInput(
                "provider", "model", "abcd", List.of(), "", List.of(),
                "", "", List.of(), 2);

        ContextTokenBreakdown breakdown = estimator.estimate(input);

        assertEquals(3, breakdown.systemPromptTokens());
        assertEquals(10, breakdown.protocolOverheadTokens());
        assertEquals(13, breakdown.totalTokens());
    }

    /**
     * 验证指纹稳定且能够感知实际工具顺序变化。
     */
    @Test
    public void testFingerprintIsStableAndOrderSensitive() {
        ContextTokenEstimator estimator = new ContextTokenEstimator(memoryProperties());
        ContextEstimationInput firstInput = inputWithTools(List.of("tool-a", "tool-b"));
        ContextEstimationInput sameInput = inputWithTools(List.of("tool-a", "tool-b"));
        ContextEstimationInput reorderedInput = inputWithTools(List.of("tool-b", "tool-a"));

        assertEquals(estimator.fingerprint(firstInput), estimator.fingerprint(sameInput));
        assertNotEquals(estimator.fingerprint(firstInput), estimator.fingerprint(reorderedInput));
        assertEquals(64, estimator.fingerprint(firstInput).length());
    }

    private ContextEstimationInput inputWithTools(List<String> tools) {
        return new ContextEstimationInput(
                "provider", "model", "prompt", tools, "summary", List.of("turn"),
                "question", "page", List.of("tool-result"), 6);
    }

    private AgentMemoryProperties memoryProperties() {
        return new AgentMemoryProperties(
                AgentMemoryProperties.STORE_TYPE_LOCAL,
                new AgentMemoryProperties.BudgetProperties(
                        1_000,
                        100,
                        new BigDecimal("0.70"),
                        new BigDecimal("0.45"),
                        new BigDecimal("0.50"),
                        new BigDecimal("1.20")),
                null,
                null);
    }
}
