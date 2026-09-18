package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.dto.agent.memory.ConversationToolCallDTO;
import com.example.chat.agent.trace.AgentExecutionTraceSnapshot;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationContextStatus;
import com.example.chat.common.enums.ConversationStatus;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.config.AgentMemoryProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 面向本地开发和单元测试的会话事实存储。
 */
@Component
@ConditionalOnProperty(
        prefix = "agent.memory",
        name = "store-type",
        havingValue = AgentMemoryProperties.STORE_TYPE_LOCAL,
        matchIfMissing = true)
public class LocalConversationStore implements ConversationStore {

    /** 会话状态的互斥访问锁。 */
    private final ReentrantLock stateLock = new ReentrantLock();
    /** 按用户和会话标识保存的会话。 */
    private final Map<ConversationKey, LocalConversation> conversations = new HashMap<>();
    /** 按任务标识保存的轮次。 */
    private final Map<String, ConversationTurnDTO> turnsByTaskId = new HashMap<>();
    /** 下一个会话主键。 */
    private long nextConversationId = 1L;
    /** 下一个轮次主键。 */
    private long nextTurnId = 1L;
    /** 下一个模型调用主键。 */
    private long nextModelCallId = 1L;
    /** 下一个工具调用主键。 */
    private long nextToolCallId = 1L;
    /** 已完成的执行事实刷新结果。 */
    private final Map<Long, ExecutionTraceFlushResult> traceFlushResults = new HashMap<>();

    @Override
    public Optional<ConversationSnapshotDTO> loadSnapshot(String userId, String sessionId) {
        validateIdentity(userId, sessionId);
        stateLock.lock();
        try {
            LocalConversation conversation = conversations.get(buildConversationKey(userId, sessionId));
            return conversation == null ? Optional.empty() : Optional.of(toSnapshot(conversation));
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public Optional<ConversationTurnDTO> findTurnByTaskId(String userId, String taskId) {
        validateText(userId, "userId");
        validateText(taskId, "taskId");
        stateLock.lock();
        try {
            ConversationTurnDTO turn = turnsByTaskId.get(taskId);
            if (turn == null) {
                return Optional.empty();
            }
            LocalConversation conversation = findConversationById(turn.conversationId());
            return conversation != null && userId.equals(conversation.userId)
                    ? Optional.of(turn) : Optional.empty();
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public Optional<ConversationExecutionLease> acquireLease(
            String userId,
            String sessionId,
            String owner,
            LocalDateTime currentTime,
            LocalDateTime expireTime) {
        validateIdentity(userId, sessionId);
        validateText(owner, "owner");
        validateLeaseTimes(currentTime, expireTime);
        stateLock.lock();
        try {
            LocalConversation conversation = conversations.computeIfAbsent(
                    buildConversationKey(userId, sessionId),
                    ignored -> createConversation(userId, sessionId));
            boolean leaseAvailable = conversation.executionOwner == null
                    || conversation.executionExpireTime == null
                    || !conversation.executionExpireTime.isAfter(currentTime);
            if (!leaseAvailable) {
                return Optional.empty();
            }
            conversation.executionEpoch++;
            conversation.executionOwner = owner;
            conversation.executionExpireTime = expireTime;
            return Optional.of(toLease(conversation));
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public boolean renewLease(
            ConversationExecutionLease lease,
            LocalDateTime newExpireTime,
            LocalDateTime currentTime) {
        validateLease(lease);
        validateLeaseTimes(currentTime, newExpireTime);
        stateLock.lock();
        try {
            LocalConversation conversation = findConversationById(lease.conversationId());
            if (!leaseMatches(conversation, lease, currentTime)) {
                return false;
            }
            conversation.executionExpireTime = newExpireTime;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public boolean releaseLease(ConversationExecutionLease lease) {
        validateLease(lease);
        stateLock.lock();
        try {
            LocalConversation conversation = findConversationById(lease.conversationId());
            if (!leaseIdentityMatches(conversation, lease)) {
                return false;
            }
            conversation.executionOwner = null;
            conversation.executionExpireTime = null;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public ConversationTurnDTO createProcessingTurn(
            String userId,
            String sessionId,
            String taskId,
            String userContent,
            int userTokenEstimate,
            ConversationExecutionLease lease) {
        validateIdentity(userId, sessionId);
        validateText(taskId, "taskId");
        validateText(userContent, "userContent");
        validateTokenEstimate(userTokenEstimate);
        validateLease(lease);
        stateLock.lock();
        try {
            ConversationTurnDTO existingTurn = turnsByTaskId.get(taskId);
            if (existingTurn != null) {
                LocalConversation existingConversation = findConversationById(existingTurn.conversationId());
                boolean sameConversation = existingConversation != null
                        && userId.equals(existingConversation.userId)
                        && sessionId.equals(existingConversation.sessionId);
                if (!sameConversation) {
                    throw new IllegalStateException("taskId 已被其他会话使用");
                }
                return existingTurn;
            }
            LocalConversation conversation = conversations.get(buildConversationKey(userId, sessionId));
            if (!leaseMatches(conversation, lease, LocalDateTime.now())) {
                throw new IllegalStateException("会话执行租约无效或已经过期");
            }
            conversation.latestTurnNo++;
            ConversationTurnDTO turn = new ConversationTurnDTO(
                    nextTurnId++, conversation.id, conversation.latestTurnNo, taskId,
                    userContent, null, ConversationTurnStatus.PROCESSING,
                    CompressionLevel.NONE, userTokenEstimate, 0, null,
                    null, null, null, List.of());
            conversation.turns.put(turn.turnNo(), turn);
            turnsByTaskId.put(taskId, turn);
            return turn;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 在本地事实存储中幂等刷新模型和工具调用记录。
     *
     * @param snapshot 执行事实快照
     * @param lease 当前会话租约
     * @return 刷新结果
     */
    @Override
    public ExecutionTraceFlushResult flushExecutionTrace(
            AgentExecutionTraceSnapshot snapshot,
            ConversationExecutionLease lease) {
        if (snapshot == null || snapshot.scope() == null) {
            throw new IllegalArgumentException("执行事实快照不能为空");
        }
        validateLease(lease);
        stateLock.lock();
        try {
            Long turnId = snapshot.scope().turnId();
            ExecutionTraceFlushResult existing = traceFlushResults.get(turnId);
            if (existing != null) {
                return existing;
            }
            LocalConversation conversation = findConversationById(lease.conversationId());
            if (!leaseMatches(conversation, lease, LocalDateTime.now())) {
                throw new IllegalStateException("会话执行租约无效或已经过期");
            }
            ConversationTurnDTO currentTurn = findTurnById(conversation, turnId);
            if (currentTurn == null
                    || currentTurn.status() != ConversationTurnStatus.PROCESSING) {
                throw new IllegalStateException("仅执行中的 Turn 可以刷新执行事实");
            }
            Long finalModelCallId = null;
            for (com.example.chat.agent.trace.ModelCallTraceSnapshot modelCall
                    : snapshot.modelCalls()) {
                long modelCallId = nextModelCallId++;
                if (modelCall.finalResponse()) {
                    finalModelCallId = modelCallId;
                }
            }
            List<ConversationToolCallDTO> toolCalls = snapshot.toolCalls().stream()
                    .map(toolCall -> new ConversationToolCallDTO(
                            nextToolCallId++,
                            turnId,
                            toolCall.agentStepNo(),
                            toolCall.callNo(),
                            toolCall.callId(),
                            toolCall.toolName(),
                            toolCall.argumentsContent(),
                            toolCall.status(),
                            toolCall.resultSummary(),
                            toolCall.resultReference(),
                            toolCall.errorCode(),
                            toolCall.errorMessage(),
                            toolCall.latencyMillis(),
                            toolCall.startTime(),
                            toolCall.finishTime()))
                    .toList();
            ConversationTurnDTO tracedTurn = new ConversationTurnDTO(
                    currentTurn.id(),
                    currentTurn.conversationId(),
                    currentTurn.turnNo(),
                    currentTurn.taskId(),
                    currentTurn.userContent(),
                    currentTurn.assistantContent(),
                    currentTurn.status(),
                    currentTurn.compressionLevel(),
                    currentTurn.userTokenEstimate(),
                    currentTurn.assistantTokenEstimate(),
                    currentTurn.finalModelCallId(),
                    currentTurn.errorCode(),
                    currentTurn.errorMessage(),
                    currentTurn.finishTime(),
                    toolCalls);
            replaceTurn(conversation, tracedTurn);
            ExecutionTraceFlushResult result = new ExecutionTraceFlushResult(
                    snapshot.modelCalls().size(),
                    toolCalls.size(),
                    finalModelCallId);
            traceFlushResults.put(turnId, result);
            return result;
        } finally {
            stateLock.unlock();
        }
    }
    public boolean completeTurn(
            Long turnId,
            String assistantContent,
            int assistantTokenEstimate,
            Long finalModelCallId,
            CompressionLevel compressionLevel,
            ConversationExecutionLease lease) {
        validateTurnCompletion(
                turnId, assistantContent, assistantTokenEstimate, compressionLevel, lease);
        stateLock.lock();
        try {
            LocalConversation conversation = findConversationById(lease.conversationId());
            if (!leaseMatches(conversation, lease, LocalDateTime.now())) {
                return false;
            }
            ConversationTurnDTO currentTurn = findTurnById(conversation, turnId);
            if (currentTurn == null || currentTurn.status() != ConversationTurnStatus.PROCESSING) {
                return false;
            }
            ConversationTurnDTO completedTurn = new ConversationTurnDTO(
                    currentTurn.id(), currentTurn.conversationId(), currentTurn.turnNo(),
                    currentTurn.taskId(), currentTurn.userContent(), assistantContent,
                    ConversationTurnStatus.SUCCESS, compressionLevel,
                    currentTurn.userTokenEstimate(), assistantTokenEstimate, finalModelCallId,
                    null, null, LocalDateTime.now(), currentTurn.toolCalls());
            replaceTurn(conversation, completedTurn);
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public boolean terminateTurn(
            Long turnId,
            ConversationTurnStatus terminalStatus,
            String errorCode,
            String errorMessage,
            ConversationExecutionLease lease) {
        validateTerminalStatus(terminalStatus);
        validateLease(lease);
        stateLock.lock();
        try {
            LocalConversation conversation = findConversationById(lease.conversationId());
            if (!leaseMatches(conversation, lease, LocalDateTime.now())) {
                return false;
            }
            ConversationTurnDTO currentTurn = findTurnById(conversation, turnId);
            if (currentTurn == null || currentTurn.status() != ConversationTurnStatus.PROCESSING) {
                return false;
            }
            ConversationTurnDTO terminalTurn = new ConversationTurnDTO(
                    currentTurn.id(), currentTurn.conversationId(), currentTurn.turnNo(),
                    currentTurn.taskId(), currentTurn.userContent(), null, terminalStatus,
                    currentTurn.compressionLevel(), currentTurn.userTokenEstimate(),
                    currentTurn.assistantTokenEstimate(), null, errorCode, errorMessage,
                    LocalDateTime.now(), currentTurn.toolCalls());
            replaceTurn(conversation, terminalTurn);
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    private LocalConversation createConversation(String userId, String sessionId) {
        LocalConversation conversation = new LocalConversation();
        conversation.id = nextConversationId++;
        conversation.userId = userId;
        conversation.sessionId = sessionId;
        return conversation;
    }

    private ConversationSnapshotDTO toSnapshot(LocalConversation conversation) {
        List<ConversationTurnDTO> terminalTurns = new ArrayList<>();
        for (ConversationTurnDTO turn : conversation.turns.values()) {
            if (turn.status() != ConversationTurnStatus.PROCESSING) {
                terminalTurns.add(turn);
            }
        }
        return new ConversationSnapshotDTO(
                conversation.id,
                conversation.userId,
                conversation.sessionId,
                ConversationStatus.ACTIVE,
                ConversationContextStatus.NORMAL,
                conversation.latestTurnNo,
                0,
                1,
                null,
                terminalTurns);
    }

    private ConversationExecutionLease toLease(LocalConversation conversation) {
        return new ConversationExecutionLease(
                conversation.id,
                conversation.userId,
                conversation.sessionId,
                conversation.executionOwner,
                conversation.executionEpoch,
                conversation.executionExpireTime);
    }

    private LocalConversation findConversationById(Long conversationId) {
        for (LocalConversation conversation : conversations.values()) {
            if (conversation.id.equals(conversationId)) {
                return conversation;
            }
        }
        return null;
    }

    private ConversationTurnDTO findTurnById(LocalConversation conversation, Long turnId) {
        if (conversation == null) {
            return null;
        }
        for (ConversationTurnDTO turn : conversation.turns.values()) {
            if (turn.id().equals(turnId)) {
                return turn;
            }
        }
        return null;
    }

    private void replaceTurn(LocalConversation conversation, ConversationTurnDTO turn) {
        conversation.turns.put(turn.turnNo(), turn);
        turnsByTaskId.put(turn.taskId(), turn);
    }

    private boolean leaseMatches(
            LocalConversation conversation,
            ConversationExecutionLease lease,
            LocalDateTime currentTime) {
        return leaseIdentityMatches(conversation, lease)
                && conversation.executionExpireTime != null
                && !conversation.executionExpireTime.isBefore(currentTime);
    }

    private boolean leaseIdentityMatches(
            LocalConversation conversation,
            ConversationExecutionLease lease) {
        return conversation != null
                && lease.owner().equals(conversation.executionOwner)
                && lease.executionEpoch().equals(conversation.executionEpoch);
    }

    private ConversationKey buildConversationKey(String userId, String sessionId) {
        return new ConversationKey(userId, sessionId);
    }

    private void validateIdentity(String userId, String sessionId) {
        validateText(userId, "userId");
        validateText(sessionId, "sessionId");
    }

    private void validateText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    private void validateLease(ConversationExecutionLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("会话执行租约不能为空");
        }
    }

    private void validateLeaseTimes(LocalDateTime currentTime, LocalDateTime expireTime) {
        if (currentTime == null || expireTime == null || !expireTime.isAfter(currentTime)) {
            throw new IllegalArgumentException("租约过期时间必须晚于当前时间");
        }
    }

    private void validateTokenEstimate(int tokenEstimate) {
        if (tokenEstimate < 0) {
            throw new IllegalArgumentException("Token 估算值不能为负数");
        }
    }

    private void validateTurnCompletion(
            Long turnId,
            String assistantContent,
            int assistantTokenEstimate,
            CompressionLevel compressionLevel,
            ConversationExecutionLease lease) {
        if (turnId == null || assistantContent == null || compressionLevel == null) {
            throw new IllegalArgumentException("完成轮次的必要参数不能为空");
        }
        validateTokenEstimate(assistantTokenEstimate);
        validateLease(lease);
    }

    private void validateTerminalStatus(ConversationTurnStatus terminalStatus) {
        if (terminalStatus != ConversationTurnStatus.FAILED
                && terminalStatus != ConversationTurnStatus.CANCELLED) {
            throw new IllegalArgumentException("轮次只能转换为 FAILED 或 CANCELLED");
        }
    }

    /**
     * 用户与会话标识组成的无碰撞本地存储键。
     *
     * @param userId 用户标识
     * @param sessionId 会话标识
     */
    private record ConversationKey(String userId, String sessionId) {
    }

    /**
     * 本地会话可变状态，仅在 stateLock 保护下访问。
     */
    private static final class LocalConversation {

        /** 会话主键。 */
        private Long id;
        /** 用户标识。 */
        private String userId;
        /** 会话标识。 */
        private String sessionId;
        /** 已分配最大轮次号。 */
        private int latestTurnNo;
        /** 当前租约持有者。 */
        private String executionOwner;
        /** 当前隔离栅栏版本。 */
        private Long executionEpoch = 0L;
        /** 当前租约过期时间。 */
        private LocalDateTime executionExpireTime;
        /** 按轮次号保存的轮次。 */
        private final Map<Integer, ConversationTurnDTO> turns = new LinkedHashMap<>();
    }
}
