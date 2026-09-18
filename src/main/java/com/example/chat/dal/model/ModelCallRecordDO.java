package com.example.chat.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型调用数据库映射对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallRecordDO {

    /** 主键。 */
    private Long id;
    /** 所属会话主键。 */
    private Long conversationId;
    /** 所属轮次主键。 */
    private Long turnId;
    /** 调用链路标识。 */
    private String taskId;
    /** 供应方请求标识。 */
    private String providerRequestId;
    /** 模型调用用途码。 */
    private Integer callType;
    /** Agent 推理步骤号。 */
    private Integer agentStepNo;
    /** 同一步骤调用尝试序号。 */
    private Integer attemptNo;
    /** 模型调用状态码。 */
    private Integer status;
    /** 派生结果应用状态码。 */
    private Integer applyStatus;
    /** 模型供应方编码。 */
    private String providerCode;
    /** 模型路由标识。 */
    private String routeId;
    /** 模型协议。 */
    private String apiProtocol;
    /** 模型名称。 */
    private String modelName;
    /** 调用前估算输入 Token。 */
    private Integer estimatedInputTokens;
    /** 实际输入 Token。 */
    private Integer inputTokens;
    /** 实际输出 Token。 */
    private Integer outputTokens;
    /** 隐藏推理 Token。 */
    private Integer reasoningTokens;
    /** 缓存命中输入 Token。 */
    private Integer cachedInputTokens;
    /** 实际总 Token。 */
    private Integer totalTokens;
    /** 模型上下文窗口。 */
    private Integer contextWindow;
    /** 最大输出 Token。 */
    private Integer maxOutputTokens;
    /** 完整回放历史终点。 */
    private Integer historyEndTurnNo;
    /** 压缩计划覆盖终点。 */
    private Integer compressionEndTurnNo;
    /** usage 是否可作增量估算锚点。 */
    private Boolean anchorReusable;
    /** 使用的摘要版本。 */
    private Integer summaryVersion;
    /** 使用的上下文版本。 */
    private Integer contextVersion;
    /** 上下文前缀指纹。 */
    private String contextFingerprint;
    /** 系统提示词版本。 */
    private String systemPromptVersion;
    /** 系统提示词内容哈希。 */
    private String systemPromptHash;
    /** 工具定义版本。 */
    private String toolDefinitionVersion;
    /** 实际工具定义哈希。 */
    private String toolSchemaHash;
    /** 调用时压缩等级码。 */
    private Integer compressionLevel;
    /** 调用耗时毫秒数。 */
    private Long latencyMillis;
    /** 模型错误码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
