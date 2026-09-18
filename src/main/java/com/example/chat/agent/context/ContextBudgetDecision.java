package com.example.chat.agent.context;

import com.example.chat.common.enums.ContextErrorCode;
import com.example.chat.common.enums.ContextZone;

/**
 * 上下文预算的不可变决策结果。
 *
 * @param breakdown Token 分段明细
 * @param estimatedInputTokens 预计输入 Token
 * @param hardInputLimit 输入硬限制
 * @param safeInputLimit 输入安全阈值
 * @param compressionTargetLimit 普通压缩目标阈值
 * @param safetyMarginTokens 安全余量
 * @param minimumRequiredTokens 理论最小上下文 Token
 * @param zone 上下文区域
 * @param errorCode 拒绝区域的分类错误码
 * @param contextFingerprint 上下文前缀指纹
 */
public record ContextBudgetDecision(
        ContextTokenBreakdown breakdown,
        int estimatedInputTokens,
        int hardInputLimit,
        int safeInputLimit,
        int compressionTargetLimit,
        int safetyMarginTokens,
        int minimumRequiredTokens,
        ContextZone zone,
        ContextErrorCode errorCode,
        String contextFingerprint) {

    /**
     * 校验预算决策的必要字段。
     */
    public ContextBudgetDecision {
        if (breakdown == null || zone == null) {
            throw new IllegalArgumentException("预算明细和上下文区域不能为空");
        }
        if (estimatedInputTokens < 0 || hardInputLimit <= 0 || safeInputLimit <= 0
                || compressionTargetLimit <= 0 || safetyMarginTokens < 0
                || minimumRequiredTokens < 0) {
            throw new IllegalArgumentException("预算数值不合法");
        }
        contextFingerprint = contextFingerprint == null ? "" : contextFingerprint;
    }

    /**
     * 判断输入是否处于安全水位。
     *
     * @return 处于安全水位时返回 true
     */
    public boolean withinSafeLimit() {
        return zone == ContextZone.SAFE;
    }

    /**
     * 判断输入是否超过硬限制。
     *
     * @return 超过硬限制时返回 true
     */
    public boolean hardLimitExceeded() {
        return zone == ContextZone.REJECT;
    }
}
