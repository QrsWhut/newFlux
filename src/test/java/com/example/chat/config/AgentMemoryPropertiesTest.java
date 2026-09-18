package com.example.chat.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Agent 记忆配置测试。
 */
public class AgentMemoryPropertiesTest {

    /**
     * 验证空配置使用安全默认值。
     */
    @Test
    public void testDefaultValues() {
        AgentMemoryProperties properties = new AgentMemoryProperties(null, null, null, null);

        assertEquals(AgentMemoryProperties.STORE_TYPE_LOCAL, properties.storeType());
        assertEquals(128_000, properties.budget().contextWindowTokens());
        assertEquals(4_096, properties.budget().maxOutputTokens());
        assertEquals(new BigDecimal("0.70"), properties.budget().safeInputRatio());
        assertEquals(3, properties.compression().minimumRecentTurns());
        assertEquals(Duration.ofSeconds(30L), properties.lease().duration());
    }

    /**
     * 验证压缩目标比例必须严格小于安全比例。
     */
    @Test
    public void testRejectInvalidRatios() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMemoryProperties(
                AgentMemoryProperties.STORE_TYPE_LOCAL,
                new AgentMemoryProperties.BudgetProperties(
                        1_000,
                        100,
                        new BigDecimal("0.60"),
                        new BigDecimal("0.60"),
                        new BigDecimal("0.50"),
                        new BigDecimal("1.20")),
                null,
                null));
    }

    /**
     * 验证续租间隔必须小于租约期限。
     */
    @Test
    public void testRejectInvalidLeaseInterval() {
        assertThrows(IllegalArgumentException.class, () -> new AgentMemoryProperties(
                AgentMemoryProperties.STORE_TYPE_LOCAL,
                null,
                null,
                new AgentMemoryProperties.LeaseProperties(
                        Duration.ofSeconds(10L), Duration.ofSeconds(10L))));
    }
}
