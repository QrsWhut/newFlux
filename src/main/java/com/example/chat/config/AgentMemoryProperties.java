package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Agent 会话记忆、上下文预算与执行租约配置。
 *
 * @param storeType 会话存储类型
 * @param budget 上下文预算配置
 * @param compression 摘要压缩配置
 * @param lease 会话执行租约配置
 */
@ConfigurationProperties(prefix = "agent.memory")
public record AgentMemoryProperties(
        String storeType,
        BudgetProperties budget,
        CompressionProperties compression,
        LeaseProperties lease) {

    /** 本地存储类型。 */
    public static final String STORE_TYPE_LOCAL = "local";
    /** MySQL 存储类型。 */
    public static final String STORE_TYPE_MYSQL = "mysql";
    /** 支持的存储类型。 */
    private static final Set<String> SUPPORTED_STORE_TYPES = Set.of(
            STORE_TYPE_LOCAL, STORE_TYPE_MYSQL);

    /**
     * 规范化并校验 Agent 记忆配置。
     */
    public AgentMemoryProperties {
        storeType = normalizeStoreType(storeType);
        budget = budget == null ? new BudgetProperties(null, null, null, null, null, null) : budget;
        compression = compression == null
                ? new CompressionProperties(null, null, null, null, null, null) : compression;
        lease = lease == null ? new LeaseProperties(null, null) : lease;
    }

    private static String normalizeStoreType(String storeType) {
        String normalizedStoreType = storeType == null || storeType.isBlank()
                ? STORE_TYPE_LOCAL : storeType.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_STORE_TYPES.contains(normalizedStoreType)) {
            throw new IllegalArgumentException("agent.memory.store-type 仅支持 local 或 mysql");
        }
        return normalizedStoreType;
    }

    /**
     * 上下文 Token 预算配置。
     *
     * @param contextWindowTokens 默认上下文窗口
     * @param maxOutputTokens 默认最大输出 Token
     * @param safeInputRatio 安全输入比例
     * @param compressionTargetRatio 普通压缩目标比例
     * @param tokenPerByteFactor UTF-8 字节估算系数
     * @param tokenCalibrationFactor Token 估算校准系数
     */
    public record BudgetProperties(
            Integer contextWindowTokens,
            Integer maxOutputTokens,
            BigDecimal safeInputRatio,
            BigDecimal compressionTargetRatio,
            BigDecimal tokenPerByteFactor,
            BigDecimal tokenCalibrationFactor) {

        /** 默认上下文窗口。 */
        private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 128_000;
        /** 默认最大输出 Token。 */
        private static final int DEFAULT_MAX_OUTPUT_TOKENS = 4_096;
        /** 默认安全输入比例。 */
        private static final BigDecimal DEFAULT_SAFE_INPUT_RATIO = new BigDecimal("0.70");
        /** 默认普通压缩目标比例。 */
        private static final BigDecimal DEFAULT_COMPRESSION_TARGET_RATIO = new BigDecimal("0.45");
        /** 默认 UTF-8 字节估算系数。 */
        private static final BigDecimal DEFAULT_TOKEN_PER_BYTE_FACTOR = new BigDecimal("0.50");
        /** 默认 Token 估算校准系数。 */
        private static final BigDecimal DEFAULT_TOKEN_CALIBRATION_FACTOR = new BigDecimal("1.20");

        /**
         * 应用默认值并校验预算配置。
         */
        public BudgetProperties {
            contextWindowTokens = contextWindowTokens == null
                    ? DEFAULT_CONTEXT_WINDOW_TOKENS : contextWindowTokens;
            maxOutputTokens = maxOutputTokens == null
                    ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
            safeInputRatio = safeInputRatio == null
                    ? DEFAULT_SAFE_INPUT_RATIO : safeInputRatio;
            compressionTargetRatio = compressionTargetRatio == null
                    ? DEFAULT_COMPRESSION_TARGET_RATIO : compressionTargetRatio;
            tokenPerByteFactor = tokenPerByteFactor == null
                    ? DEFAULT_TOKEN_PER_BYTE_FACTOR : tokenPerByteFactor;
            tokenCalibrationFactor = tokenCalibrationFactor == null
                    ? DEFAULT_TOKEN_CALIBRATION_FACTOR : tokenCalibrationFactor;
            validatePositive(contextWindowTokens, "context-window-tokens");
            validatePositive(maxOutputTokens, "max-output-tokens");
            if (maxOutputTokens >= contextWindowTokens) {
                throw new IllegalArgumentException("max-output-tokens 必须小于 context-window-tokens");
            }
            validateRatio(safeInputRatio, "safe-input-ratio");
            validateRatio(compressionTargetRatio, "compression-target-ratio");
            if (compressionTargetRatio.compareTo(safeInputRatio) >= 0) {
                throw new IllegalArgumentException("compression-target-ratio 必须小于 safe-input-ratio");
            }
            validatePositive(tokenPerByteFactor, "token-per-byte-factor");
            validatePositive(tokenCalibrationFactor, "token-calibration-factor");
        }

        private static void validatePositive(int value, String propertyName) {
            if (value <= 0) {
                throw new IllegalArgumentException(propertyName + " 必须大于 0");
            }
        }

        private static void validatePositive(BigDecimal value, String propertyName) {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(propertyName + " 必须大于 0");
            }
        }

        private static void validateRatio(BigDecimal value, String propertyName) {
            if (value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException(propertyName + " 必须大于 0 且小于 1");
            }
        }
    }

    /**
     * 会话摘要压缩配置。
     *
     * @param minimumRecentTurns 最少保留的近期成功轮次
     * @param compressionContextWindowTokens 压缩模型上下文窗口
     * @param maxCompressionSummaryTokens 普通摘要最大 Token
     * @param maxDeepSummaryTokens 深度摘要最大 Token
     * @param compressionSafetyMarginTokens 压缩请求安全余量
     * @param maxPublishRetries 摘要发布冲突最大重评次数
     */
    public record CompressionProperties(
            Integer minimumRecentTurns,
            Integer compressionContextWindowTokens,
            Integer maxCompressionSummaryTokens,
            Integer maxDeepSummaryTokens,
            Integer compressionSafetyMarginTokens,
            Integer maxPublishRetries) {

        /** 默认近期完整轮次数量。 */
        private static final int DEFAULT_MINIMUM_RECENT_TURNS = 3;
        /** 默认压缩上下文窗口。 */
        private static final int DEFAULT_COMPRESSION_CONTEXT_WINDOW_TOKENS = 128_000;
        /** 默认普通摘要最大 Token。 */
        private static final int DEFAULT_MAX_COMPRESSION_SUMMARY_TOKENS = 2_048;
        /** 默认深度摘要最大 Token。 */
        private static final int DEFAULT_MAX_DEEP_SUMMARY_TOKENS = 1_024;
        /** 默认压缩请求安全余量。 */
        private static final int DEFAULT_COMPRESSION_SAFETY_MARGIN_TOKENS = 1_024;
        /** 默认摘要发布冲突重评次数。 */
        private static final int DEFAULT_MAX_PUBLISH_RETRIES = 2;

        /**
         * 应用默认值并校验压缩配置。
         */
        public CompressionProperties {
            minimumRecentTurns = valueOrDefault(
                    minimumRecentTurns, DEFAULT_MINIMUM_RECENT_TURNS);
            compressionContextWindowTokens = valueOrDefault(
                    compressionContextWindowTokens, DEFAULT_COMPRESSION_CONTEXT_WINDOW_TOKENS);
            maxCompressionSummaryTokens = valueOrDefault(
                    maxCompressionSummaryTokens, DEFAULT_MAX_COMPRESSION_SUMMARY_TOKENS);
            maxDeepSummaryTokens = valueOrDefault(
                    maxDeepSummaryTokens, DEFAULT_MAX_DEEP_SUMMARY_TOKENS);
            compressionSafetyMarginTokens = valueOrDefault(
                    compressionSafetyMarginTokens, DEFAULT_COMPRESSION_SAFETY_MARGIN_TOKENS);
            maxPublishRetries = valueOrDefault(maxPublishRetries, DEFAULT_MAX_PUBLISH_RETRIES);
            if (maxDeepSummaryTokens > maxCompressionSummaryTokens) {
                throw new IllegalArgumentException(
                        "max-deep-summary-tokens 不能大于 max-compression-summary-tokens");
            }
            long requiredCompressionTokens = (long) maxCompressionSummaryTokens
                    + compressionSafetyMarginTokens;
            if (requiredCompressionTokens >= compressionContextWindowTokens) {
                throw new IllegalArgumentException("压缩摘要预算与安全余量必须小于压缩上下文窗口");
            }
        }

        private static int valueOrDefault(Integer value, int defaultValue) {
            int normalizedValue = value == null ? defaultValue : value;
            if (normalizedValue <= 0) {
                throw new IllegalArgumentException("压缩配置数值必须大于 0");
            }
            return normalizedValue;
        }
    }

    /**
     * 会话执行租约配置。
     *
     * @param duration 租约有效期
     * @param renewInterval 续租间隔
     */
    public record LeaseProperties(Duration duration, Duration renewInterval) {

        /** 默认租约有效期。 */
        private static final Duration DEFAULT_DURATION = Duration.ofSeconds(30L);
        /** 默认续租间隔。 */
        private static final Duration DEFAULT_RENEW_INTERVAL = Duration.ofSeconds(10L);

        /**
         * 应用默认值并校验租约配置。
         */
        public LeaseProperties {
            duration = duration == null ? DEFAULT_DURATION : duration;
            renewInterval = renewInterval == null ? DEFAULT_RENEW_INTERVAL : renewInterval;
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("lease.duration 必须大于 0");
            }
            if (renewInterval.isZero() || renewInterval.isNegative()) {
                throw new IllegalArgumentException("lease.renew-interval 必须大于 0");
            }
            if (renewInterval.compareTo(duration) >= 0) {
                throw new IllegalArgumentException("lease.renew-interval 必须小于 lease.duration");
            }
        }
    }
}
