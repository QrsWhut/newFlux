package com.example.chat.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 会话数据库映射对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversationDO {

    /** 主键。 */
    private Long id;
    /** 用户标识。 */
    private String userId;
    /** 会话标识。 */
    private String sessionId;
    /** 会话状态码。 */
    private Integer status;
    /** 上下文治理状态码。 */
    private Integer contextStatus;
    /** 已分配最大轮次号。 */
    private Integer latestTurnNo;
    /** 最新有效摘要主键。 */
    private Long latestSummaryId;
    /** 最新摘要版本。 */
    private Integer latestSummaryVersion;
    /** 上下文语义版本。 */
    private Integer contextVersion;
    /** 乐观锁版本。 */
    private Integer lockVersion;
    /** 执行租约持有者。 */
    private String executionOwner;
    /** 执行租约隔离栅栏版本。 */
    private Long executionEpoch;
    /** 执行租约过期时间。 */
    private LocalDateTime executionExpireTime;
    /** 模型供应方编码。 */
    private String providerCode;
    /** 模型路由标识。 */
    private String routeId;
    /** 模型协议。 */
    private String apiProtocol;
    /** 模型名称。 */
    private String modelName;
    /** 系统提示词版本。 */
    private String systemPromptVersion;
    /** 系统提示词内容哈希。 */
    private String systemPromptHash;
    /** 工具定义版本。 */
    private String toolDefinitionVersion;
    /** 实际工具定义哈希。 */
    private String toolSchemaHash;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
