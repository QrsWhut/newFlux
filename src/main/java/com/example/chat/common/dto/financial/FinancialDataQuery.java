package com.example.chat.common.dto.financial;

import com.example.chat.common.enums.FinancialAssetType;
import com.example.chat.common.enums.FinancialDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 金融数据查询请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDataQuery {

    /** 完整自然语言问题。 */
    private String question;
    /** 金融实体名称或 Wind 标准代码。 */
    private String entity;
    /** 指标名称列表。 */
    private List<String> metricNames;
    /** 用户提供的原始时间范围。 */
    private String timeRange;
    /** 资产类型。 */
    private FinancialAssetType assetType;
    /** 数据查询类型。 */
    private FinancialDataType dataType;
    /** 查询开始日期。 */
    private String beginDate;
    /** 查询结束日期。 */
    private String endDate;
    /** 最近观测期数。 */
    private String observation;
    /** 当前会话标识。 */
    private String sessionId;

    /**
     * 获取有效资产类型。
     *
     * @return 资产类型
     */
    public FinancialAssetType resolveAssetType() {
        return assetType == null ? FinancialAssetType.AUTO : assetType;
    }

    /**
     * 获取有效数据查询类型。
     *
     * @return 数据查询类型
     */
    public FinancialDataType resolveDataType() {
        return dataType == null ? FinancialDataType.AUTO : dataType;
    }
}
