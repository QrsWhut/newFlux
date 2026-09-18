package com.example.chat.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话轮次数据库映射对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurnDO {

    /** 主键。 */
    private Long id;
    /** 所属会话主键。 */
    private Long conversationId;
    /** 会话内轮次号。 */
    private Integer turnNo;
    /** 单次请求幂等标识。 */
    private String taskId;
    /** 用户显式输入。 */
    private String userContent;
    /** 助手最终显式回答。 */
    private String assistantContent;
    /** 轮次状态码。 */
    private Integer status;
    /** 压缩等级码。 */
    private Integer compressionLevel;
    /** 用户输入估算 Token。 */
    private Integer userTokenEstimate;
    /** 助手回答估算 Token。 */
    private Integer assistantTokenEstimate;
    /** 最终成功主模型调用主键。 */
    private Long finalModelCallId;
    /** 脱敏错误码。 */
    private String errorCode;
    /** 脱敏错误摘要。 */
    private String errorMessage;
    /** 终态时间。 */
    private LocalDateTime finishTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
