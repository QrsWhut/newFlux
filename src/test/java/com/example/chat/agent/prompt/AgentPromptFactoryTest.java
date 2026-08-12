package com.example.chat.agent.prompt;

import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.memory.ConversationTurn;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.common.dto.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentPromptFactory 提示词与上下文组装单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class AgentPromptFactoryTest {

    @Test
    public void testBuildInitialMessagesSequence() {
        AgentPromptFactory factory = new AgentPromptFactory();

        ChatRequest request = new ChatRequest(
                "t1", "s1", "u1", "当前问题：茅台PE是多少？",
                Collections.emptyList(), Map.of("pageData", "页面显示: 股票代码600519")
        );

        ConversationTurn turn1 = ConversationTurn.builder().userQuestion("看下茅台").assistantAnswer("好的，为您查看茅台。").build();
        ConversationMemory memory = ConversationMemory.builder()
                .summary("早期对话摘要：关注白酒板块")
                .recentTurns(List.of(turn1))
                .build();

        List<AgentMessage> messages = factory.buildInitialMessages(request, memory);

        // 预期顺序: 0=system(系统提示词), 1=system(页面上下文), 2=system(较早轮次摘要), 3=user(turn1问), 4=assistant(turn1答), 5=user(当前问题)
        assertEquals(6, messages.size());

        assertEquals("system", messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("你是金融对话助手"));

        assertEquals("system", messages.get(1).getRole());
        assertTrue(messages.get(1).getContent().contains("600519"));

        assertEquals("system", messages.get(2).getRole());
        assertTrue(messages.get(2).getContent().contains("关注白酒板块"));

        assertEquals("user", messages.get(3).getRole());
        assertEquals("看下茅台", messages.get(3).getContent());

        assertEquals("assistant", messages.get(4).getRole());
        assertEquals("好的，为您查看茅台。", messages.get(4).getContent());

        assertEquals("user", messages.get(5).getRole());
        assertEquals("当前问题：茅台PE是多少？", messages.get(5).getContent());
    }
}
