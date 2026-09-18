package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.agent.trace.AgentExecutionTraceSnapshot;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationTurnStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 会话事实存储同步接口。
 *
 * <p>MySQL 实现允许使用阻塞 Mapper，响应式隔离由上层 Manager 统一负责。</p>
 */
public interface ConversationStore {

    /**
     * 加载会话最新摘要及摘要后的终态轮次。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @return 会话快照，不存在时返回空
     */
    Optional<ConversationSnapshotDTO> loadSnapshot(String userId, String sessionId);

    /**
     * 按可信用户和任务标识查询轮次。
     *
     * @param userId 用户标识
     * @param taskId 任务标识
     * @return 对应轮次，不存在或不属于该用户时返回空
     */
    Optional<ConversationTurnDTO> findTurnByTaskId(String userId, String taskId);

    /**
     * 获取会话执行租约，新租约必须推进 fencing epoch。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param owner 租约持有者
     * @param currentTime 当前时间
     * @param expireTime 租约过期时间
     * @return 成功取得的租约，会话繁忙时返回空
     */
    Optional<ConversationExecutionLease> acquireLease(
            String userId,
            String sessionId,
            String owner,
            LocalDateTime currentTime,
            LocalDateTime expireTime);

    /**
     * 续租当前会话执行权。
     *
     * @param lease 当前租约
     * @param newExpireTime 新过期时间
     * @param currentTime 当前时间
     * @return 续租成功时返回 true
     */
    boolean renewLease(
            ConversationExecutionLease lease,
            LocalDateTime newExpireTime,
            LocalDateTime currentTime);

    /**
     * 释放当前会话执行权。
     *
     * @param lease 当前租约
     * @return 释放成功时返回 true
     */
    boolean releaseLease(ConversationExecutionLease lease);

    /**
     * 在有效租约下幂等创建执行中轮次。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param taskId 任务幂等标识
     * @param userContent 用户显式输入
     * @param userTokenEstimate 用户输入估算 Token
     * @param lease 当前会话租约
     * @return 新建或已存在的轮次
     */
    ConversationTurnDTO createProcessingTurn(
            String userId,
            String sessionId,
            String taskId,
            String userContent,
            int userTokenEstimate,
            ConversationExecutionLease lease);

    /**
     * 在有效租约下完成轮次。
     *
     * @param turnId 轮次主键
     * @param assistantContent 助手最终显式回答
     * @param assistantTokenEstimate 助手回答估算 Token
     * @param finalModelCallId 最终成功模型调用主键
     * @param compressionLevel 本轮压缩等级
     * @param lease 当前会话租约
     * @return 条件更新成功时返回 true
     */
    /**
     * 在有效租约下幂等写入当前 Turn 的模型与工具事实。
     *
     * @param snapshot 执行事实快照
     * @param lease 当前会话租约
     * @return 刷新结果
     */
    ExecutionTraceFlushResult flushExecutionTrace(
            AgentExecutionTraceSnapshot snapshot,
            ConversationExecutionLease lease);
    boolean completeTurn(
            Long turnId,
            String assistantContent,
            int assistantTokenEstimate,
            Long finalModelCallId,
            CompressionLevel compressionLevel,
            ConversationExecutionLease lease);

    /**
     * 在有效租约下将轮次转换为失败或取消终态。
     *
     * @param turnId 轮次主键
     * @param terminalStatus 失败或取消状态
     * @param errorCode 脱敏错误码
     * @param errorMessage 脱敏错误摘要
     * @param lease 当前会话租约
     * @return 条件更新成功时返回 true
     */
    boolean terminateTurn(
            Long turnId,
            ConversationTurnStatus terminalStatus,
            String errorCode,
            String errorMessage,
            ConversationExecutionLease lease);
}
