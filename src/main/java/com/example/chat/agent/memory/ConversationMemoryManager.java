package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.agent.trace.AgentExecutionTraceSnapshot;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.config.AgentMemoryProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 会话事实存储的响应式边界服务。
 *
 * <p>所有同步 Store 调用均切换到 boundedElastic，避免阻塞 WebFlux 事件循环。</p>
 */
@Service
public class ConversationMemoryManager {

    /** 同步会话事实存储。 */
    private final ConversationStore conversationStore;
    /** Agent 记忆配置。 */
    private final AgentMemoryProperties memoryProperties;

    /**
     * 创建会话记忆 Manager。
     *
     * @param conversationStore 同步会话事实存储
     * @param memoryProperties Agent 记忆配置
     */
    public ConversationMemoryManager(
            ConversationStore conversationStore,
            AgentMemoryProperties memoryProperties) {
        this.conversationStore = conversationStore;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 加载会话最新快照。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @return 会话快照 Optional
     */
    public Mono<Optional<ConversationSnapshotDTO>> loadSnapshot(
            String userId,
            String sessionId) {
        return blockingCall(() -> conversationStore.loadSnapshot(userId, sessionId));
    }

    /**
     * 按可信用户和任务标识查询轮次。
     *
     * @param userId 用户标识
     * @param taskId 任务标识
     * @return 会话轮次 Optional
     */
    public Mono<Optional<ConversationTurnDTO>> findTurnByTaskId(
            String userId,
            String taskId) {
        return blockingCall(() -> conversationStore.findTurnByTaskId(userId, taskId));
    }

    /**
     * 使用配置的租约期限获取会话执行权。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param owner 租约持有者
     * @return 会话执行租约 Optional
     */
    public Mono<Optional<ConversationExecutionLease>> acquireLease(
            String userId,
            String sessionId,
            String owner) {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expireTime = currentTime.plus(memoryProperties.lease().duration());
        return acquireLease(userId, sessionId, owner, currentTime, expireTime);
    }

    /**
     * 使用指定时间边界获取会话执行权。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param owner 租约持有者
     * @param currentTime 当前时间
     * @param expireTime 租约过期时间
     * @return 会话执行租约 Optional
     */
    public Mono<Optional<ConversationExecutionLease>> acquireLease(
            String userId,
            String sessionId,
            String owner,
            LocalDateTime currentTime,
            LocalDateTime expireTime) {
        return blockingCall(() -> conversationStore.acquireLease(
                userId, sessionId, owner, currentTime, expireTime));
    }

    /**
     * 使用配置的租约期限续租。
     *
     * @param lease 当前会话执行租约
     * @return 续租成功时返回 true
     */
    public Mono<Boolean> renewLease(ConversationExecutionLease lease) {
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime expireTime = currentTime.plus(memoryProperties.lease().duration());
        return renewLease(lease, expireTime, currentTime);
    }

    /**
     * 使用指定时间边界续租。
     *
     * @param lease 当前会话执行租约
     * @param newExpireTime 新过期时间
     * @param currentTime 当前时间
     * @return 续租成功时返回 true
     */
    public Mono<Boolean> renewLease(
            ConversationExecutionLease lease,
            LocalDateTime newExpireTime,
            LocalDateTime currentTime) {
        return blockingCall(() -> conversationStore.renewLease(
                lease, newExpireTime, currentTime));
    }

    /**
     * 释放会话执行权。
     *
     * @param lease 当前会话执行租约
     * @return 释放成功时返回 true
     */
    public Mono<Boolean> releaseLease(ConversationExecutionLease lease) {
        return blockingCall(() -> conversationStore.releaseLease(lease));
    }

    /**
     * 在有效租约下幂等创建执行中轮次。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     * @param taskId 任务幂等标识
     * @param userContent 用户显式输入
     * @param userTokenEstimate 用户输入估算 Token
     * @param lease 当前会话执行租约
     * @return 新建或已存在的轮次
     */
    public Mono<ConversationTurnDTO> createProcessingTurn(
            String userId,
            String sessionId,
            String taskId,
            String userContent,
            int userTokenEstimate,
            ConversationExecutionLease lease) {
        return blockingCall(() -> conversationStore.createProcessingTurn(
                userId,
                sessionId,
                taskId,
                userContent,
                userTokenEstimate,
                lease));
    }

    /**
     * 在有效租约下完成轮次。
     *
     * @param turnId 轮次主键
     * @param assistantContent 助手最终显式回答
     * @param assistantTokenEstimate 助手回答估算 Token
     * @param finalModelCallId 最终成功模型调用主键
     * @param compressionLevel 本轮压缩等级
     * @param lease 当前会话执行租约
     * @return 条件更新成功时返回 true
     */
    /**
     * 在阻塞边界内刷新当前 Turn 的执行事实。
     *
     * @param snapshot 执行事实快照
     * @param lease 当前会话租约
     * @return 刷新结果
     */
    public Mono<ExecutionTraceFlushResult> flushExecutionTrace(
            AgentExecutionTraceSnapshot snapshot,
            ConversationExecutionLease lease) {
        return blockingCall(() -> conversationStore.flushExecutionTrace(
                snapshot, lease));
    }
    public Mono<Boolean> completeTurn(
            Long turnId,
            String assistantContent,
            int assistantTokenEstimate,
            Long finalModelCallId,
            CompressionLevel compressionLevel,
            ConversationExecutionLease lease) {
        return blockingCall(() -> conversationStore.completeTurn(
                turnId,
                assistantContent,
                assistantTokenEstimate,
                finalModelCallId,
                compressionLevel,
                lease));
    }

    /**
     * 在有效租约下将轮次转换为失败或取消终态。
     *
     * @param turnId 轮次主键
     * @param terminalStatus 失败或取消状态
     * @param errorCode 脱敏错误码
     * @param errorMessage 脱敏错误摘要
     * @param lease 当前会话执行租约
     * @return 条件更新成功时返回 true
     */
    public Mono<Boolean> terminateTurn(
            Long turnId,
            ConversationTurnStatus terminalStatus,
            String errorCode,
            String errorMessage,
            ConversationExecutionLease lease) {
        return blockingCall(() -> conversationStore.terminateTurn(
                turnId, terminalStatus, errorCode, errorMessage, lease));
    }

    private <T> Mono<T> blockingCall(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
