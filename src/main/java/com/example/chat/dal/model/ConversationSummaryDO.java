package com.example.chat.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话摘要数据库映射对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryDO {

    /** 主键。 */
    private Long id;
    /** 所属会话主键。 */
    private Long conversationId;
    /** 摘要版本。 */
    private Integer summaryVersion;
    /** 来源摘要主键。 */
    private Long previousSummaryId;
    /** 覆盖起始轮次。 */
    private Integer coveredStartTurnNo;
    /** 覆盖结束轮次。 */
    private Integer coveredEndTurnNo;
    /** 摘要正文。 */
    private String summaryContent;
    /** 摘要估算 Token。 */
    private Integer summaryTokenEstimate;
    /** 压缩等级码。 */
    private Integer compressionLevel;
    /** 压缩模型调用主键。 */
    private Long modelCallId;
    /** 压缩模型供应方编码。 */
    private String providerCode;
    /** 压缩模型路由标识。 */
    private String routeId;
    /** 压缩模型协议。 */
    private String apiProtocol;
    /** 压缩模型名称。 */
    private String modelName;
    /** 摘要提示词版本。 */
    private String promptVersion;
    /** 摘要提示词内容哈希。 */
    private String promptHash;
    /** 摘要状态码。 */
    private Integer status;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
