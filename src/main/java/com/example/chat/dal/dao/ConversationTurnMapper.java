package com.example.chat.dal.dao;

import com.example.chat.dal.model.ConversationTurnDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话轮次 MyBatis Mapper。
 */
public interface ConversationTurnMapper {

    /**
     * 插入会话轮次。
     *
     * @param turn 会话轮次记录
     * @return 影响行数
     */
    int insert(ConversationTurnDO turn);

    /**
     * 按主键查询轮次。
     *
     * @param id 轮次主键
     * @return 轮次记录，不存在时返回 null
     */
    ConversationTurnDO selectById(@Param("id") Long id);

    /**
     * 按可信用户和任务标识查询轮次。
     *
     * @param userId 用户标识
     * @param taskId 任务标识
     * @return 轮次记录，不存在或不属于该用户时返回 null
     */
    ConversationTurnDO selectByUserAndTaskId(
            @Param("userId") String userId,
            @Param("taskId") String taskId);

    /**
     * 查询摘要终点后的全部终态轮次。
     *
     * @param conversationId 会话主键
     * @param afterTurnNo 摘要覆盖终点
     * @param processingStatus 执行中状态码
     * @return 按轮次号升序排列的终态轮次
     */
    List<ConversationTurnDO> selectTerminalAfterTurnNo(
            @Param("conversationId") Long conversationId,
            @Param("afterTurnNo") Integer afterTurnNo,
            @Param("processingStatus") Integer processingStatus);

    /**
     * 在有效租约下将轮次更新为成功终态。
     *
     * @param turnId 轮次主键
     * @param processingStatus 执行中状态码
     * @param successStatus 成功状态码
     * @param assistantContent 助手最终显式回答
     * @param assistantTokenEstimate 助手回答估算 Token
     * @param finalModelCallId 最终成功模型调用主键
     * @param compressionLevel 压缩等级码
     * @param finishTime 完成时间
     * @param leaseOwner 租约持有者
     * @param executionEpoch 隔离栅栏版本
     * @param currentTime 当前时间
     * @return 影响行数
     */
    int completeTurn(
            @Param("turnId") Long turnId,
            @Param("processingStatus") Integer processingStatus,
            @Param("successStatus") Integer successStatus,
            @Param("assistantContent") String assistantContent,
            @Param("assistantTokenEstimate") Integer assistantTokenEstimate,
            @Param("finalModelCallId") Long finalModelCallId,
            @Param("compressionLevel") Integer compressionLevel,
            @Param("finishTime") LocalDateTime finishTime,
            @Param("leaseOwner") String leaseOwner,
            @Param("executionEpoch") Long executionEpoch,
            @Param("currentTime") LocalDateTime currentTime);

    /**
     * 在有效租约下将轮次更新为失败或取消终态。
     *
     * @param turnId 轮次主键
     * @param processingStatus 执行中状态码
     * @param terminalStatus 目标终态码
     * @param errorCode 脱敏错误码
     * @param errorMessage 脱敏错误摘要
     * @param finishTime 完成时间
     * @param leaseOwner 租约持有者
     * @param executionEpoch 隔离栅栏版本
     * @param currentTime 当前时间
     * @return 影响行数
     */
    int terminateTurn(
            @Param("turnId") Long turnId,
            @Param("processingStatus") Integer processingStatus,
            @Param("terminalStatus") Integer terminalStatus,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishTime") LocalDateTime finishTime,
            @Param("leaseOwner") String leaseOwner,
            @Param("executionEpoch") Long executionEpoch,
            @Param("currentTime") LocalDateTime currentTime);
}
