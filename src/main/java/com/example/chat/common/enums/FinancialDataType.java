package com.example.chat.common.enums;

import org.springframework.util.StringUtils;

/**
 * 金融数据查询类型。
 */
public enum FinancialDataType {

    /** 根据问题内容自动识别。 */
    AUTO,
    /** 当前行情快照。 */
    SNAPSHOT,
    /** 历史 K 线。 */
    KLINE,
    /** 分钟行情。 */
    QUOTE,
    /** 基本档案。 */
    BASIC_INFO,
    /** 财务与估值基本面。 */
    FUNDAMENTALS,
    /** 股东或基金持仓。 */
    HOLDINGS,
    /** 基金或资产业绩。 */
    PERFORMANCE,
    /** 技术指标。 */
    TECHNICAL,
    /** 风险指标。 */
    RISK,
    /** 公司行动或事件。 */
    EVENTS,
    /** 股票或基金筛选。 */
    SCREENING,
    /** 跨实体聚合或复合计算。 */
    AGGREGATION;

    /**
     * 将可选文本转换为枚举。
     *
     * @param value 枚举文本
     * @return 数据查询类型
     */
    public static FinancialDataType fromValue(String value) {
        return StringUtils.hasText(value)
                ? FinancialDataType.valueOf(value.trim().toUpperCase()) : AUTO;
    }
}
