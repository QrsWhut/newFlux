package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationSummaryDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.config.AgentMemoryProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 依据摘要覆盖终点和近期成功轮次生成普通或深度压缩候选范围。
 */
@Component
public class ConversationCompactionPlanner {

    /** Agent 记忆配置。 */
    private final AgentMemoryProperties memoryProperties;

    /**
     * 创建会话压缩规划器。
     *
     * @param memoryProperties Agent 记忆配置
     */
    public ConversationCompactionPlanner(AgentMemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    /**
     * 使用配置的近期成功轮次数量生成普通压缩计划。
     *
     * @param snapshot 会话快照
     * @return 有候选旧轮次时返回计划
     */
    public Optional<CompactionPlan> planNormal(ConversationSnapshotDTO snapshot) {
        return planNormal(snapshot, memoryProperties.compression().minimumRecentTurns());
    }

    /**
     * 生成普通压缩计划，至少保留指定数量的近期成功轮次。
     *
     * @param snapshot 会话快照
     * @param minimumRecentTurns 最少保留的近期成功轮次
     * @return 有候选旧轮次时返回计划
     */
    public Optional<CompactionPlan> planNormal(
            ConversationSnapshotDTO snapshot,
            int minimumRecentTurns) {
        if (minimumRecentTurns <= 0) {
            throw new IllegalArgumentException("minimumRecentTurns 必须大于 0");
        }
        List<ConversationTurnDTO> continuousTurns = continuousTerminalTurns(snapshot);
        List<ConversationTurnDTO> successfulTurns = continuousTurns.stream()
                .filter(turn -> turn.status() == ConversationTurnStatus.SUCCESS)
                .toList();
        if (successfulTurns.size() < minimumRecentTurns) {
            return Optional.empty();
        }
        int firstPreservedSuccessIndex = successfulTurns.size() - minimumRecentTurns;
        int preservedStartTurnNo = successfulTurns.get(firstPreservedSuccessIndex).turnNo();
        List<ConversationTurnDTO> eligibleTurns = continuousTurns.stream()
                .filter(turn -> turn.turnNo() < preservedStartTurnNo)
                .toList();
        if (eligibleTurns.isEmpty()) {
            return Optional.empty();
        }
        ConversationSummaryDTO previousSummary = snapshot.latestSummary();
        int coveredStartTurnNo = previousSummary == null
                ? eligibleTurns.get(0).turnNo() : previousSummary.coveredStartTurnNo();
        int coveredEndTurnNo = eligibleTurns.get(eligibleTurns.size() - 1).turnNo();
        return Optional.of(new CompactionPlan(
                CompressionLevel.NORMAL,
                previousSummary,
                coveredStartTurnNo,
                coveredEndTurnNo,
                eligibleTurns,
                true));
    }

    /**
     * 生成深度压缩计划，允许仅缩短当前摘要并保持覆盖终点不变。
     *
     * @param snapshot 会话快照
     * @return 存在旧摘要或连续终态轮次时返回计划
     */
    public Optional<CompactionPlan> planDeep(ConversationSnapshotDTO snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        ConversationSummaryDTO previousSummary = snapshot.latestSummary();
        List<ConversationTurnDTO> continuousTurns = continuousTerminalTurns(snapshot);
        if (continuousTurns.isEmpty() && previousSummary == null) {
            return Optional.empty();
        }
        if (continuousTurns.isEmpty()) {
            return Optional.of(new CompactionPlan(
                    CompressionLevel.DEEP,
                    previousSummary,
                    previousSummary.coveredStartTurnNo(),
                    previousSummary.coveredEndTurnNo(),
                    List.of(),
                    false));
        }
        int coveredStartTurnNo = previousSummary == null
                ? continuousTurns.get(0).turnNo() : previousSummary.coveredStartTurnNo();
        int coveredEndTurnNo = continuousTurns.get(continuousTurns.size() - 1).turnNo();
        boolean coverageAdvances = previousSummary == null
                || coveredEndTurnNo > previousSummary.coveredEndTurnNo();
        return Optional.of(new CompactionPlan(
                CompressionLevel.DEEP,
                previousSummary,
                coveredStartTurnNo,
                coveredEndTurnNo,
                continuousTurns,
                coverageAdvances));
    }

    private List<ConversationTurnDTO> continuousTerminalTurns(ConversationSnapshotDTO snapshot) {
        if (snapshot == null || snapshot.turns().isEmpty()) {
            return List.of();
        }
        int summaryEndTurnNo = snapshot.latestSummary() == null
                ? 0 : snapshot.latestSummary().coveredEndTurnNo();
        int expectedTurnNo = summaryEndTurnNo + 1;
        List<ConversationTurnDTO> sortedTurns = snapshot.turns().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ConversationTurnDTO::turnNo))
                .toList();
        List<ConversationTurnDTO> continuousTurns = new ArrayList<>();
        for (ConversationTurnDTO turn : sortedTurns) {
            if (turn.turnNo() < expectedTurnNo) {
                continue;
            }
            if (turn.turnNo() != expectedTurnNo
                    || turn.status() == ConversationTurnStatus.PROCESSING) {
                break;
            }
            validateTerminalStatus(turn);
            continuousTurns.add(turn);
            expectedTurnNo++;
        }
        return List.copyOf(continuousTurns);
    }

    private void validateTerminalStatus(ConversationTurnDTO turn) {
        if (turn.status() != ConversationTurnStatus.SUCCESS
                && turn.status() != ConversationTurnStatus.FAILED
                && turn.status() != ConversationTurnStatus.CANCELLED) {
            throw new IllegalStateException("不支持的摘要候选轮次状态: " + turn.status());
        }
    }
}
