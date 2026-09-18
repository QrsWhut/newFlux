package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationTurnStatus;
import com.example.chat.config.AgentMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话记忆 Manager 与本地事实存储测试。
 */
public class ConversationMemoryManagerTest {

    /** 被测会话记忆 Manager。 */
    private ConversationMemoryManager memoryManager;

    /**
     * 使用本地事实存储创建被测对象。
     */
    @BeforeEach
    public void setUp() {
        AgentMemoryProperties properties = new AgentMemoryProperties(null, null, null, null);
        memoryManager = new ConversationMemoryManager(new LocalConversationStore(), properties);
    }

    /**
     * 验证 PROCESSING 到 SUCCESS、幂等创建和快照读取。
     */
    @Test
    public void testCreateCompleteAndLoadTurn() {
        ConversationExecutionLease lease = acquireLease("owner-a");
        ConversationTurnDTO createdTurn = memoryManager.createProcessingTurn(
                        "user-a", "session-a", "task-a", "问题", 10, lease)
                .block();
        ConversationTurnDTO duplicateTurn = memoryManager.createProcessingTurn(
                        "user-a", "session-a", "task-a", "问题", 10, lease)
                .block();

        assertEquals(createdTurn.id(), duplicateTurn.id());
        StepVerifier.create(memoryManager.completeTurn(
                        createdTurn.id(), "回答", 8, 100L, CompressionLevel.NONE, lease))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(memoryManager.findTurnByTaskId("user-a", "task-a"))
                .assertNext(optionalTurn -> {
                    assertTrue(optionalTurn.isPresent());
                    assertEquals(ConversationTurnStatus.SUCCESS, optionalTurn.get().status());
                    assertEquals("回答", optionalTurn.get().assistantContent());
                })
                .verifyComplete();
        StepVerifier.create(memoryManager.loadSnapshot("user-a", "session-a"))
                .assertNext(optionalSnapshot -> {
                    assertTrue(optionalSnapshot.isPresent());
                    assertEquals(1, optionalSnapshot.get().turns().size());
                })
                .verifyComplete();
    }

    /**
     * 验证租约过期后的新 epoch 能够隔离旧持有者写入。
     */
    @Test
    public void testExpiredLeaseFencesOldOwner() {
        LocalDateTime startTime = LocalDateTime.now();
        ConversationExecutionLease oldLease = memoryManager.acquireLease(
                        "user-a", "session-a", "owner-a",
                        startTime, startTime.plusSeconds(1L))
                .block()
                .orElseThrow();
        ConversationTurnDTO turn = memoryManager.createProcessingTurn(
                        "user-a", "session-a", "task-a", "问题", 10, oldLease)
                .block();
        ConversationExecutionLease newLease = memoryManager.acquireLease(
                        "user-a", "session-a", "owner-b",
                        startTime.plusSeconds(2L), startTime.plusSeconds(30L))
                .block()
                .orElseThrow();

        assertTrue(newLease.executionEpoch() > oldLease.executionEpoch());
        assertFalse(memoryManager.completeTurn(
                        turn.id(), "旧持有者回答", 8, 100L, CompressionLevel.NONE, oldLease)
                .block());
        assertTrue(memoryManager.terminateTurn(
                        turn.id(), ConversationTurnStatus.CANCELLED,
                        "LEASE_EXPIRED", "旧租约已经失效", newLease)
                .block());
    }

    private ConversationExecutionLease acquireLease(String owner) {
        return memoryManager.acquireLease("user-a", "session-a", owner)
                .block()
                .orElseThrow();
    }
}
