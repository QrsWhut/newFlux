package com.example.chat.common.enums;

import org.springframework.util.StringUtils;

/**
 * 金融资产类型。
 */
public enum FinancialAssetType {

    /** 根据问题内容自动识别。 */
    AUTO,
    /** 股票。 */
    STOCK,
    /** 基金、ETF 或 LOF。 */
    FUND,
    /** 指数或板块。 */
    INDEX,
    /** 债券。 */
    BOND,
    /** 宏观、行业或汇率经济指标。 */
    ECONOMIC;

    /**
     * 将可选文本转换为枚举。
     *
     * @param value 枚举文本
     * @return 资产类型
     */
    public static FinancialAssetType fromValue(String value) {
        return StringUtils.hasText(value)
                ? FinancialAssetType.valueOf(value.trim().toUpperCase()) : AUTO;
    }
}
