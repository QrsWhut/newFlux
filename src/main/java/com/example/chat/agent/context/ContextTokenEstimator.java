package com.example.chat.agent.context;

import com.example.chat.config.AgentMemoryProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 基于 UTF-8 字节与模型校准系数的上下文 Token 估算器。
 */
@Component
public class ContextTokenEstimator {

    /** SHA-256 摘要算法名称。 */
    private static final String SHA_256_ALGORITHM = "SHA-256";
    /** 单条协议消息的默认固定开销。 */
    private static final int MESSAGE_PROTOCOL_OVERHEAD_TOKENS = 4;

    /** 上下文预算配置。 */
    private final AgentMemoryProperties memoryProperties;

    /**
     * 创建上下文 Token 估算器。
     *
     * @param memoryProperties Agent 记忆配置
     */
    public ContextTokenEstimator(AgentMemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /**
     * 对上下文的每个组成部分进行 Token 估算。
     *
     * @param input 不可变上下文输入
     * @return Token 分段明细
     */
    public ContextTokenBreakdown estimate(ContextEstimationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("上下文估算输入不能为空");
        }
        AgentMemoryProperties.BudgetProperties budgetProperties = memoryProperties.budget();
        int protocolOverheadTokens = estimateProtocolOverhead(
                input.messageCount(), budgetProperties.tokenCalibrationFactor());
        return new ContextTokenBreakdown(
                estimateText(input.systemPrompt(), budgetProperties),
                estimateTexts(input.toolDefinitions(), budgetProperties),
                estimateText(input.summary(), budgetProperties),
                estimateTexts(input.uncoveredTurns(), budgetProperties),
                estimateText(input.currentUserContent(), budgetProperties),
                estimateText(input.pageContext(), budgetProperties),
                estimateTexts(input.currentTurnToolMessages(), budgetProperties),
                protocolOverheadTokens);
    }

    /**
     * 计算当前上下文输入的稳定 SHA-256 指纹。
     *
     * @param input 不可变上下文输入
     * @return 小写十六进制指纹
     */
    public String fingerprint(ContextEstimationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("上下文估算输入不能为空");
        }
        MessageDigest digest = createDigest();
        updateDigest(digest, input.providerCode());
        updateDigest(digest, input.modelName());
        updateDigest(digest, input.systemPrompt());
        updateDigest(digest, input.toolDefinitions());
        updateDigest(digest, input.summary());
        updateDigest(digest, input.uncoveredTurns());
        updateDigest(digest, input.currentUserContent());
        updateDigest(digest, input.pageContext());
        updateDigest(digest, input.currentTurnToolMessages());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(input.messageCount()).array());
        return toHex(digest.digest());
    }

    private int estimateTexts(
            List<String> values,
            AgentMemoryProperties.BudgetProperties budgetProperties) {
        long totalTokens = 0L;
        for (String value : values) {
            totalTokens += estimateText(value, budgetProperties);
            if (totalTokens >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) totalTokens;
    }

    private int estimateText(
            String value,
            AgentMemoryProperties.BudgetProperties budgetProperties) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int utf8ByteCount = value.getBytes(StandardCharsets.UTF_8).length;
        BigDecimal estimatedTokens = BigDecimal.valueOf(utf8ByteCount)
                .multiply(budgetProperties.tokenPerByteFactor())
                .multiply(budgetProperties.tokenCalibrationFactor());
        return toBoundedTokenCount(estimatedTokens);
    }

    private int estimateProtocolOverhead(int messageCount, BigDecimal calibrationFactor) {
        BigDecimal overheadTokens = BigDecimal.valueOf(messageCount)
                .multiply(BigDecimal.valueOf(MESSAGE_PROTOCOL_OVERHEAD_TOKENS))
                .multiply(calibrationFactor);
        return toBoundedTokenCount(overheadTokens);
    }

    private int toBoundedTokenCount(BigDecimal value) {
        BigDecimal roundedValue = value.setScale(0, RoundingMode.CEILING);
        if (roundedValue.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return roundedValue.intValue();
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(SHA_256_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private void updateDigest(MessageDigest digest, List<String> values) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(values.size()).array());
        for (String value : values) {
            updateDigest(digest, value);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(value & 0x0F, 16));
        }
        return builder.toString();
    }
}
