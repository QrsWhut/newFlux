package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSnapshotDTO;
import com.example.chat.common.dto.agent.memory.ConversationSummaryDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationContextStatus;
import com.example.chat.common.enums.ConversationStatus;
import com.example.chat.common.enums.ConversationSummaryStatus;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.config.AgentMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话压缩候选规划测试。
 */
public class ConversationCompactionPlannerTest {

    /** 被测压缩规划器。 */
    private ConversationCompactionPlanner planner;

    /**
     * 创建使用默认三轮保留策略的规划器。
     */
    @BeforeEach
    public void setUp() {
        planner = new ConversationCompactionPlanner(
                new AgentMemoryProperties(null, null, null, null));
    }

    /**
     * 验证普通压缩保留最近三个成功轮次并连续夹带失败轮次。
     */
    @Test
    public void testNormalPlanKeepsRecentSuccessfulTurns() {
        ConversationSnapshotDTO snapshot = snapshot(null, List.of(
                turn(1, ConversationTurnStatus.FAILED),
                turn(2, ConversationTurnStatus.SUCCESS),
                turn(3, ConversationTurnStatus.CANCELLED),
                turn(4, ConversationTurnStatus.SUCCESS),
                turn(5, ConversationTurnStatus.FAILED),
                turn(6, ConversationTurnStatus.SUCCESS),
                turn(7, ConversationTurnStatus.CANCELLED),
                turn(8, ConversationTurnStatus.SUCCESS)));

        CompactionPlan plan = planner.planNormal(snapshot).orElseThrow();

        assertEquals(CompressionLevel.NORMAL, plan.compressionLevel());
        assertEquals(List.of(1, 2, 3), plan.turns().stream()
                .map(ConversationTurnDTO::turnNo)
                .toList());
        assertEquals(1, plan.coveredStartTurnNo());
        assertEquals(3, plan.coveredEndTurnNo());
        assertTrue(plan.coverageAdvances());
    }

    /**
     * 验证只有需要保留的三个成功轮次时没有普通压缩候选。
     */
    @Test
    public void testNormalPlanReturnsEmptyWithoutOldTurns() {
        ConversationSnapshotDTO snapshot = snapshot(null, List.of(
                turn(1, ConversationTurnStatus.SUCCESS),
                turn(2, ConversationTurnStatus.SUCCESS),
                turn(3, ConversationTurnStatus.SUCCESS)));

        Optional<CompactionPlan> plan = planner.planNormal(snapshot);

        assertTrue(plan.isEmpty());
    }

    /**
     * 验证深度压缩能够只缩短旧摘要并保持覆盖终点。
     */
    @Test
    public void testDeepPlanMayKeepCoveredEndTurnNo() {
        ConversationSummaryDTO summary = summary(1, 9);
        ConversationSnapshotDTO snapshot = snapshot(summary, List.of());

        CompactionPlan plan = planner.planDeep(snapshot).orElseThrow();

        assertEquals(CompressionLevel.DEEP, plan.compressionLevel());
        assertEquals(9, plan.coveredEndTurnNo());
        assertTrue(plan.turns().isEmpty());
        assertFalse(plan.coverageAdvances());
    }

    /**
     * 验证候选范围不会跨过未完成轮次或轮次缺口。
     */
    @Test
    public void testDeepPlanDoesNotCrossProcessingTurn() {
        ConversationSnapshotDTO snapshot = snapshot(null, List.of(
                turn(1, ConversationTurnStatus.SUCCESS),
                turn(2, ConversationTurnStatus.PROCESSING),
                turn(3, ConversationTurnStatus.SUCCESS)));

        CompactionPlan plan = planner.planDeep(snapshot).orElseThrow();

        assertEquals(List.of(1), plan.turns().stream()
                .map(ConversationTurnDTO::turnNo)
                .toList());
        assertEquals(1, plan.coveredEndTurnNo());
    }

    private ConversationSnapshotDTO snapshot(
            ConversationSummaryDTO summary,
            List<ConversationTurnDTO> turns) {
        int latestSummaryVersion = summary == null ? 0 : summary.summaryVersion();
        return new ConversationSnapshotDTO(
                1L, "user", "session", ConversationStatus.ACTIVE,
                ConversationContextStatus.NORMAL, 10, latestSummaryVersion,
                1, summary, turns);
    }

    private ConversationTurnDTO turn(int turnNo, ConversationTurnStatus status) {
        String assistantContent = status == ConversationTurnStatus.SUCCESS ? "回答" : null;
        return new ConversationTurnDTO(
                (long) turnNo, 1L, turnNo, "task-" + turnNo,
                "问题", assistantContent, status, CompressionLevel.NONE,
                5, 5, null, status.name(), null, LocalDateTime.now(), List.of());
    }

    private ConversationSummaryDTO summary(int startTurnNo, int endTurnNo) {
        return new ConversationSummaryDTO(
                1L, 1L, 1, null, startTurnNo, endTurnNo,
                "历史摘要", 20, CompressionLevel.NORMAL, 10L,
                "provider", "route", "responses", "model",
                "v1", "hash", ConversationSummaryStatus.PUBLISHED);
    }
}
