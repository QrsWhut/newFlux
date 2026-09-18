package com.example.chat.common.enums;

import org.springframework.util.StringUtils;

/**
 * 金融文档类型。
 */
public enum FinancialDocumentType {

    /** 根据问题内容自动识别。 */
    AUTO,
    /** 上市公司公告或监管披露。 */
    ANNOUNCEMENT,
    /** 财经新闻。 */
    NEWS,
    /** 券商研报。 */
    RESEARCH,
    /** 通用背景资料。 */
    BACKGROUND;

    /**
     * 将可选文本转换为枚举。
     *
     * @param value 枚举文本
     * @return 文档类型
     */
    public static FinancialDocumentType fromValue(String value) {
        return StringUtils.hasText(value)
                ? FinancialDocumentType.valueOf(value.trim().toUpperCase()) : AUTO;
    }
}
