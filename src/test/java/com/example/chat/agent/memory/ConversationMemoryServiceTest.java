package com.example.chat.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationMemoryService 三轮滑动窗口与记忆隔离单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class ConversationMemoryServiceTest {

    private ConversationMemoryService memoryService;

    @BeforeEach
    public void setUp() {
        InMemoryConversationMemoryRepository repository = new InMemoryConversationMemoryRepository();
        ConversationSummaryService summaryService = new ConversationSummaryService();
        memoryService = new ConversationMemoryService(repository, summaryService);
    }

    @Test
    public void testKeepThreeTurnsAndSummarizeFourth() {
        String userId = "u100";
        String sessionId = "s100";

        // 1. 追加第 1 轮
        StepVerifier.create(memoryService.appendTurn(userId, sessionId, "问题1", "回答1")).verifyComplete();
        // 2. 追加第 2 轮
        StepVerifier.create(memoryService.appendTurn(userId, sessionId, "问题2", "回答2")).verifyComplete();
        // 3. 追加第 3 轮
        StepVerifier.create(memoryService.appendTurn(userId, sessionId, "问题3", "回答3")).verifyComplete();

        // 验证前 3 轮完整在窗口内，摘要为空
        StepVerifier.create(memoryService.getMemory(userId, sessionId))
                .expectNextMatches(mem -> {
                    assertEquals("", mem.getSummary());
                    assertEquals(3, mem.getRecentTurns().size());
                    assertEquals("问题1", mem.getRecentTurns().get(0).getUserQuestion());
                    return true;
                })
                .verifyComplete();

        // 4. 追加第 4 轮
        StepVerifier.create(memoryService.appendTurn(userId, sessionId, "问题4", "回答4")).verifyComplete();

        // 验证第 1 轮被压缩入摘要，窗口内只有第 2, 3, 4 轮
        StepVerifier.create(memoryService.getMemory(userId, sessionId))
                .expectNextMatches(mem -> {
                    assertTrue(mem.getSummary().contains("问题1"));
                    assertEquals(3, mem.getRecentTurns().size());
                    assertEquals("问题2", mem.getRecentTurns().get(0).getUserQuestion());
                    assertEquals("问题4", mem.getRecentTurns().get(2).getUserQuestion());
                    return true;
                })
                .verifyComplete();
    }

    @Test
    public void testUserSessionIsolation() {
        StepVerifier.create(memoryService.appendTurn("u1", "s1", "A问", "A答")).verifyComplete();
        StepVerifier.create(memoryService.appendTurn("u2", "s2", "B问", "B答")).verifyComplete();

        StepVerifier.create(memoryService.getMemory("u1", "s1"))
                .expectNextMatches(mem -> mem.getRecentTurns().size() == 1 && "A问".equals(mem.getRecentTurns().get(0).getUserQuestion()))
                .verifyComplete();

        StepVerifier.create(memoryService.getMemory("u2", "s2"))
                .expectNextMatches(mem -> mem.getRecentTurns().size() == 1 && "B问".equals(mem.getRecentTurns().get(0).getUserQuestion()))
                .verifyComplete();
    }

    @Test
    public void testMissingParametersThrowsError() {
        StepVerifier.create(memoryService.getMemory("", "s1"))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(memoryService.appendTurn("u1", null, "q", "a"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
