package com.example.chat.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 轮次工具调用数据库映射对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnToolCallDO {

    /** 主键。 */
    private Long id;
    /** 所属轮次主键。 */
    private Long turnId;
    /** Agent 推理步骤号。 */
    private Integer agentStepNo;
    /** 步骤内调用顺序号。 */
    private Integer callNo;
    /** 模型工具调用标识。 */
    private String callId;
    /** 工具名称。 */
    private String toolName;
    /** 脱敏后的原始参数字符串。 */
    private String argumentsContent;
    /** 工具调用状态码。 */
    private Integer status;
    /** 可持久化结果摘要。 */
    private String resultSummary;
    /** 受控结果引用。 */
    private String resultReference;
    /** 工具错误码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 调用耗时毫秒数。 */
    private Long latencyMillis;
    /** 开始时间。 */
    private LocalDateTime startTime;
    /** 结束时间。 */
    private LocalDateTime finishTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
