package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationSummaryDTO;
import com.example.chat.common.dto.agent.memory.ConversationToolCallDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.agent.trace.AgentExecutionTraceSnapshot;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationContextStatus;
import com.example.chat.common.enums.ConversationStatus;
import com.example.chat.common.enums.ConversationSummaryStatus;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.common.enums.ToolCallStatus;
import com.example.chat.common.enums.ModelCallApplyStatus;
import com.example.chat.common.enums.ModelCallType;
import com.example.chat.config.AgentMemoryProperties;
import com.example.chat.dal.dao.AgentConversationMapper;
import com.example.chat.dal.dao.ModelCallRecordMapper;
import com.example.chat.dal.dao.ConversationSummaryMapper;
import com.example.chat.dal.dao.ConversationTurnMapper;
import com.example.chat.dal.dao.TurnToolCallMapper;
import com.example.chat.dal.model.AgentConversationDO;
import com.example.chat.dal.model.ModelCallRecordDO;
import com.example.chat.dal.model.ConversationSummaryDO;
import com.example.chat.dal.model.ConversationTurnDO;
import com.example.chat.dal.model.TurnToolCallDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * 基于 MySQL 和 MyBatis 的会话事实存储。
 */
@Component
@ConditionalOnProperty(
        prefix = "agent.memory",
        name = "store-type",
        havingValue = AgentMemoryProperties.STORE_TYPE_MYSQL)
public class MySqlConversationStore implements ConversationStore {

    /** 会话 Mapper。 */
    private final AgentConversationMapper conversationMapper;
    /** 轮次 Mapper。 */
    private final ConversationTurnMapper turnMapper;
    /** 工具调用 Mapper。 */
    private final TurnToolCallMapper toolCallMapper;
    /** 模型调用 Mapper。 */
    private final ModelCallRecordMapper modelCallMapper;
    /** 摘要 Mapper。 */
    private final ConversationSummaryMapper summaryMapper;

    /**
     * 创建 MySQL 会话事实存储。
     *
     * @param conversationMapper 会话 Mapper
     * @param turnMapper 轮次 Mapper
     * @param toolCallMapper 工具调用 Mapper
     * @param summaryMapper 摘要 Mapper
     */
    public MySqlConversationStore(
            AgentConversationMapper conversationMapper,
            ConversationTurnMapper turnMapper,
            TurnToolCallMapper toolCallMapper,
            ModelCallRecordMapper modelCallMapper,
            ConversationSummaryMapper summaryMapper) {
        this.conversationMapper = conversationMapper;
        this.turnMapper = turnMapper;
        this.toolCallMapper = toolCallMapper;
        this.modelCallMapper = modelCallMapper;
        this.summaryMapper = summaryMapper;
    }

    @Override
    public Optional<ConversationSnapshotDTO> loadSnapshot(String userId, String sessionId) {
        validateIdentity(userId, sessionId);
        AgentConversationDO conversation = conversationMapper.selectByUserAndSession(userId, sessionId);
        if (conversation == null) {
            return Optional.empty();
        }
        ConversationSummaryDO summary = conversation.getLatestSummaryId() == null
                ? null : summaryMapper.selectById(conversation.getLatestSummaryId());
        int summaryEndTurnNo = summary == null ? 0 : summary.getCoveredEndTurnNo();
        List<ConversationTurnDO> turnRecords = turnMapper.selectTerminalAfterTurnNo(
                conversation.getId(),
                summaryEndTurnNo,
                ConversationTurnStatus.PROCESSING.getCode());
        Map<Long, List<ConversationToolCallDTO>> toolCallsByTurnId = loadToolCalls(turnRecords);
        List<ConversationTurnDTO> turns = turnRecords.stream()
                .map(turn -> toTurnDTO(turn, toolCallsByTurnId.getOrDefault(turn.getId(), List.of())))
                .toList();
        return Optional.of(new ConversationSnapshotDTO(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getSessionId(),
                toConversationStatus(conversation.getStatus()),
                toContextStatus(conversation.getContextStatus()),
                conversation.getLatestTurnNo(),
                conversation.getLatestSummaryVersion(),
                conversation.getContextVersion(),
                toSummaryDTO(summary),
                turns));
    }

    @Override
    public Optional<ConversationTurnDTO> findTurnByTaskId(String userId, String taskId) {
        validateText(userId, "userId");
        validateText(taskId, "taskId");
        ConversationTurnDO turn = turnMapper.selectByUserAndTaskId(userId, taskId);
        if (turn == null) {
            return Optional.empty();
        }
        List<TurnToolCallDO> toolCalls = toolCallMapper.selectByTurnIds(List.of(turn.getId()));
        return Optional.of(toTurnDTO(turn, toolCalls.stream().map(this::toToolCallDTO).toList()));
    }

    @Override
    @Transactional
    public Optional<ConversationExecutionLease> acquireLease(
            String userId,
            String sessionId,
            String owner,
            LocalDateTime currentTime,
            LocalDateTime expireTime) {
        validateIdentity(userId, sessionId);
        validateText(owner, "owner");
        validateLeaseTimes(currentTime, expireTime);
        AgentConversationDO conversation = getOrCreateConversation(userId, sessionId);
        int updatedRows = conversationMapper.acquireLease(
                conversation.getId(), owner, currentTime, expireTime);
        if (updatedRows == 0) {
            return Optional.empty();
        }
        AgentConversationDO leasedConversation = conversationMapper.selectById(conversation.getId());
        return Optional.of(new ConversationExecutionLease(
                leasedConversation.getId(),
                leasedConversation.getUserId(),
                leasedConversation.getSessionId(),
                leasedConversation.getExecutionOwner(),
                leasedConversation.getExecutionEpoch(),
                leasedConversation.getExecutionExpireTime()));
    }

    @Override
    public boolean renewLease(
            ConversationExecutionLease lease,
            LocalDateTime newExpireTime,
            LocalDateTime currentTime) {
        validateLease(lease);
        validateLeaseTimes(currentTime, newExpireTime);
        return conversationMapper.renewLease(
                lease.conversationId(),
                lease.owner(),
                lease.executionEpoch(),
                currentTime,
                newExpireTime) == 1;
    }

    @Override
    public boolean releaseLease(ConversationExecutionLease lease) {
        validateLease(lease);
        return conversationMapper.releaseLease(
                lease.conversationId(), lease.owner(), lease.executionEpoch()) == 1;
    }

    @Override
    @Transactional
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
        ConversationTurnDO existingTurn = turnMapper.selectByUserAndTaskId(userId, taskId);
        if (existingTurn != null) {
            if (!existingTurn.getConversationId().equals(lease.conversationId())) {
                throw new IllegalStateException("taskId 已用于其他会话");
            }
            return toTurnDTO(existingTurn, loadToolCalls(existingTurn));
        }
        AgentConversationDO conversation = requireActiveLease(lease, LocalDateTime.now());
        int incrementedRows = conversationMapper.incrementLatestTurnNo(
                conversation.getId(), conversation.getLockVersion());
        if (incrementedRows != 1) {
            throw new IllegalStateException("分配会话轮次号时发生并发冲突");
        }
        AgentConversationDO updatedConversation = conversationMapper.selectById(conversation.getId());
        ConversationTurnDO turn = ConversationTurnDO.builder()
                .conversationId(updatedConversation.getId())
                .turnNo(updatedConversation.getLatestTurnNo())
                .taskId(taskId)
                .userContent(userContent)
                .status(ConversationTurnStatus.PROCESSING.getCode())
                .compressionLevel(CompressionLevel.NONE.getCode())
                .userTokenEstimate(userTokenEstimate)
                .assistantTokenEstimate(0)
                .build();
        if (turnMapper.insert(turn) != 1) {
            throw new IllegalStateException("创建执行中轮次失败");
        }
        return toTurnDTO(turn, List.of());
    }

    /**
     * 在同一事务内幂等写入模型与工具调用事实。
     *
     * @param snapshot 执行事实快照
     * @param lease 当前会话租约
     * @return 刷新结果
     */
    @Override
    @Transactional
    public ExecutionTraceFlushResult flushExecutionTrace(
            AgentExecutionTraceSnapshot snapshot,
            ConversationExecutionLease lease) {
        if (snapshot == null || snapshot.scope() == null) {
            throw new IllegalArgumentException("执行事实快照不能为空");
        }
        validateLease(lease);
        AgentConversationDO conversation =
                conversationMapper.selectById(lease.conversationId());
        LocalDateTime currentTime = LocalDateTime.now();
        if (conversation == null
                || !Objects.equals(lease.owner(), conversation.getExecutionOwner())
                || !Objects.equals(
                        lease.executionEpoch(), conversation.getExecutionEpoch())
                || conversation.getExecutionExpireTime() == null
                || !conversation.getExecutionExpireTime().isAfter(currentTime)) {
            throw new IllegalStateException("会话执行租约无效或已经过期");
        }
        Long turnId = snapshot.scope().turnId();
        List<ModelCallRecordDO> existingModelCalls =
                modelCallMapper.selectByTurnId(turnId);
        if (!existingModelCalls.isEmpty()) {
            List<TurnToolCallDO> existingToolCalls =
                    toolCallMapper.selectByTurnIds(List.of(turnId));
            Long finalModelCallId = existingModelCalls.get(
                    existingModelCalls.size() - 1).getId();
            return new ExecutionTraceFlushResult(
                    existingModelCalls.size(),
                    existingToolCalls.size(),
                    finalModelCallId);
        }
        Long finalModelCallId = null;
        for (com.example.chat.agent.trace.ModelCallTraceSnapshot modelCall
                : snapshot.modelCalls()) {
            ModelCallRecordDO record = ModelCallRecordDO.builder()
                    .conversationId(snapshot.scope().conversationId())
                    .turnId(turnId)
                    .taskId(snapshot.scope().taskId())
                    .providerRequestId(modelCall.providerRequestId())
                    .callType(ModelCallType.MAIN.getCode())
                    .agentStepNo(modelCall.agentStepNo())
                    .attemptNo(modelCall.attemptNo())
                    .status(modelCall.status().getCode())
                    .applyStatus(ModelCallApplyStatus.NOT_APPLICABLE.getCode())
                    .providerCode(modelCall.providerCode())
                    .routeId(modelCall.routeId())
                    .apiProtocol(modelCall.apiProtocol())
                    .modelName(modelCall.modelName())
                    .estimatedInputTokens(modelCall.estimatedInputTokens())
                    .inputTokens(modelCall.inputTokens())
                    .outputTokens(modelCall.outputTokens())
                    .reasoningTokens(modelCall.reasoningTokens())
                    .cachedInputTokens(modelCall.cachedInputTokens())
                    .totalTokens(modelCall.totalTokens())
                    .contextWindow(modelCall.contextWindow())
                    .maxOutputTokens(modelCall.maxOutputTokens())
                    .historyEndTurnNo(snapshot.scope().historyEndTurnNo())
                    .anchorReusable(modelCall.anchorReusable())
                    .summaryVersion(snapshot.scope().summaryVersion())
                    .contextVersion(snapshot.scope().contextVersion())
                    .contextFingerprint(modelCall.contextFingerprint())
                    .systemPromptVersion(snapshot.scope().systemPromptVersion())
                    .systemPromptHash(snapshot.scope().systemPromptHash())
                    .toolDefinitionVersion(
                            snapshot.scope().toolDefinitionVersion())
                    .toolSchemaHash(modelCall.toolSchemaHash())
                    .compressionLevel(
                            snapshot.scope().compressionLevel().getCode())
                    .latencyMillis(modelCall.latencyMillis())
                    .errorCode(modelCall.errorCode())
                    .errorMessage(modelCall.errorMessage())
                    .build();
            if (modelCallMapper.insert(record) != 1) {
                throw new IllegalStateException("模型调用事实写入失败");
            }
            if (modelCall.finalResponse()) {
                finalModelCallId = record.getId();
            }
        }
        for (com.example.chat.agent.trace.ToolCallTraceSnapshot toolCall
                : snapshot.toolCalls()) {
            TurnToolCallDO record = TurnToolCallDO.builder()
                    .turnId(turnId)
                    .agentStepNo(toolCall.agentStepNo())
                    .callNo(toolCall.callNo())
                    .callId(toolCall.callId())
                    .toolName(toolCall.toolName())
                    .argumentsContent(toolCall.argumentsContent())
                    .status(toolCall.status().getCode())
                    .resultSummary(toolCall.resultSummary())
                    .resultReference(toolCall.resultReference())
                    .errorCode(toolCall.errorCode())
                    .errorMessage(toolCall.errorMessage())
                    .latencyMillis(toolCall.latencyMillis())
                    .startTime(toolCall.startTime())
                    .finishTime(toolCall.finishTime())
                    .build();
            if (toolCallMapper.insert(record) != 1) {
                throw new IllegalStateException("工具调用事实写入失败");
            }
        }
        return new ExecutionTraceFlushResult(
                snapshot.modelCalls().size(),
                snapshot.toolCalls().size(),
                finalModelCallId);
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
        LocalDateTime currentTime = LocalDateTime.now();
        return turnMapper.completeTurn(
                turnId,
                ConversationTurnStatus.PROCESSING.getCode(),
                ConversationTurnStatus.SUCCESS.getCode(),
                assistantContent,
                assistantTokenEstimate,
                finalModelCallId,
                compressionLevel.getCode(),
                currentTime,
                lease.owner(),
                lease.executionEpoch(),
                currentTime) == 1;
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
        LocalDateTime currentTime = LocalDateTime.now();
        return turnMapper.terminateTurn(
                turnId,
                ConversationTurnStatus.PROCESSING.getCode(),
                terminalStatus.getCode(),
                errorCode,
                errorMessage,
                currentTime,
                lease.owner(),
                lease.executionEpoch(),
                currentTime) == 1;
    }

    private AgentConversationDO getOrCreateConversation(String userId, String sessionId) {
        AgentConversationDO conversation = conversationMapper.selectByUserAndSession(userId, sessionId);
        if (conversation != null) {
            return conversation;
        }
        AgentConversationDO newConversation = AgentConversationDO.builder()
                .userId(userId)
                .sessionId(sessionId)
                .status(ConversationStatus.ACTIVE.getCode())
                .contextStatus(ConversationContextStatus.NORMAL.getCode())
                .latestTurnNo(0)
                .latestSummaryVersion(0)
                .contextVersion(1)
                .lockVersion(0)
                .executionEpoch(0L)
                .build();
        try {
            if (conversationMapper.insert(newConversation) != 1) {
                throw new IllegalStateException("创建 Agent 会话失败");
            }
            return newConversation;
        } catch (DuplicateKeyException exception) {
            AgentConversationDO concurrentConversation =
                    conversationMapper.selectByUserAndSession(userId, sessionId);
            if (concurrentConversation == null) {
                throw new IllegalStateException("并发创建 Agent 会话后无法读取会话", exception);
            }
            return concurrentConversation;
        }
    }

    private AgentConversationDO requireActiveLease(
            ConversationExecutionLease lease,
            LocalDateTime currentTime) {
        AgentConversationDO conversation = conversationMapper.selectById(lease.conversationId());
        boolean leaseValid = conversation != null
                && lease.owner().equals(conversation.getExecutionOwner())
                && lease.executionEpoch().equals(conversation.getExecutionEpoch())
                && conversation.getExecutionExpireTime() != null
                && !conversation.getExecutionExpireTime().isBefore(currentTime);
        if (!leaseValid) {
            throw new IllegalStateException("会话执行租约无效或已经过期");
        }
        return conversation;
    }

    private Map<Long, List<ConversationToolCallDTO>> loadToolCalls(
            List<ConversationTurnDO> turnRecords) {
        if (turnRecords.isEmpty()) {
            return Map.of();
        }
        List<Long> turnIds = turnRecords.stream().map(ConversationTurnDO::getId).toList();
        List<TurnToolCallDO> toolCallRecords = toolCallMapper.selectByTurnIds(turnIds);
        Map<Long, List<ConversationToolCallDTO>> toolCallsByTurnId = new HashMap<>();
        for (TurnToolCallDO toolCallRecord : toolCallRecords) {
            toolCallsByTurnId.computeIfAbsent(
                    toolCallRecord.getTurnId(), ignored -> new ArrayList<>())
                    .add(toToolCallDTO(toolCallRecord));
        }
        return toolCallsByTurnId;
    }

    private List<ConversationToolCallDTO> loadToolCalls(ConversationTurnDO turn) {
        return toolCallMapper.selectByTurnIds(List.of(turn.getId())).stream()
                .map(this::toToolCallDTO)
                .toList();
    }

    private ConversationTurnDTO toTurnDTO(
            ConversationTurnDO turn,
            List<ConversationToolCallDTO> toolCalls) {
        return new ConversationTurnDTO(
                turn.getId(),
                turn.getConversationId(),
                turn.getTurnNo(),
                turn.getTaskId(),
                turn.getUserContent(),
                turn.getAssistantContent(),
                toTurnStatus(turn.getStatus()),
                toCompressionLevel(turn.getCompressionLevel()),
                turn.getUserTokenEstimate(),
                turn.getAssistantTokenEstimate(),
                turn.getFinalModelCallId(),
                turn.getErrorCode(),
                turn.getErrorMessage(),
                turn.getFinishTime(),
                toolCalls);
    }

    private ConversationToolCallDTO toToolCallDTO(TurnToolCallDO toolCall) {
        return new ConversationToolCallDTO(
                toolCall.getId(),
                toolCall.getTurnId(),
                toolCall.getAgentStepNo(),
                toolCall.getCallNo(),
                toolCall.getCallId(),
                toolCall.getToolName(),
                toolCall.getArgumentsContent(),
                toToolCallStatus(toolCall.getStatus()),
                toolCall.getResultSummary(),
                toolCall.getResultReference(),
                toolCall.getErrorCode(),
                toolCall.getErrorMessage(),
                toolCall.getLatencyMillis(),
                toolCall.getStartTime(),
                toolCall.getFinishTime());
    }

    private ConversationSummaryDTO toSummaryDTO(ConversationSummaryDO summary) {
        if (summary == null) {
            return null;
        }
        return new ConversationSummaryDTO(
                summary.getId(),
                summary.getConversationId(),
                summary.getSummaryVersion(),
                summary.getPreviousSummaryId(),
                summary.getCoveredStartTurnNo(),
                summary.getCoveredEndTurnNo(),
                summary.getSummaryContent(),
                summary.getSummaryTokenEstimate(),
                toCompressionLevel(summary.getCompressionLevel()),
                summary.getModelCallId(),
                summary.getProviderCode(),
                summary.getRouteId(),
                summary.getApiProtocol(),
                summary.getModelName(),
                summary.getPromptVersion(),
                summary.getPromptHash(),
                toSummaryStatus(summary.getStatus()));
    }

    private ConversationStatus toConversationStatus(Integer code) {
        return EnumSet.allOf(ConversationStatus.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知会话状态码: " + code));
    }

    private ConversationContextStatus toContextStatus(Integer code) {
        return EnumSet.allOf(ConversationContextStatus.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知上下文状态码: " + code));
    }

    private ConversationTurnStatus toTurnStatus(Integer code) {
        return EnumSet.allOf(ConversationTurnStatus.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知轮次状态码: " + code));
    }

    private CompressionLevel toCompressionLevel(Integer code) {
        return EnumSet.allOf(CompressionLevel.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知压缩等级码: " + code));
    }

    private ToolCallStatus toToolCallStatus(Integer code) {
        return EnumSet.allOf(ToolCallStatus.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知工具调用状态码: " + code));
    }

    private ConversationSummaryStatus toSummaryStatus(Integer code) {
        return EnumSet.allOf(ConversationSummaryStatus.class).stream()
                .filter(value -> value.getCode() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知摘要状态码: " + code));
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
}
