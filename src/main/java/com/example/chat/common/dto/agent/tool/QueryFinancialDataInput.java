package com.example.chat.common.dto.agent.tool;

import com.example.chat.common.annotation.AgentToolParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 金融数据查询工具输入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryFinancialDataInput {

    /** 查询主体和意图。 */
    @NotBlank(message = "query 不能为空")
    @Size(max = 500)
    @AgentToolParam(description = "用于查询行情或数据计算的标的名和问题")
    private String query;

    /** 可选指标名称。 */
    @Size(max = 20)
    @AgentToolParam(description = "需要查询的指标名称列表，例如市盈率、营业收入")
    private List<@Size(max = 100, message = "单个指标名称长度不能超过 100") String> metricNames;

    /** 可选时间范围。 */
    @Size(max = 100)
    @AgentToolParam(description = "查询时间范围，例如最近一年、2025年")
    private String timeRange;
}
