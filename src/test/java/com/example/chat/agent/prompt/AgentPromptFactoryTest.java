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

        ConversationTurn turn1 = ConversationTurn.builder()
                .messages(List.of(
                        AgentMessage.user("看下茅台"),
                        AgentMessage.assistant("好的，为您查看茅台。")))
                .build();
        ConversationMemory memory = ConversationMemory.builder()
                .summary("早期对话摘要：关注白酒板块")
                .recentTurns(List.of(turn1))
                .build();

        List<AgentMessage> messages = factory.buildInitialMessages(request, memory);

        // 预期顺序：开发者指令、摘要、历史用户、历史助手、当前用户。
        assertEquals(5, messages.size());

        assertEquals("developer", messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("你是金融对话助手"));

        assertEquals("developer", messages.get(1).getRole());
        assertTrue(messages.get(1).getContent().contains("关注白酒板块"));

        assertEquals("user", messages.get(2).getRole());
        assertEquals("看下茅台", messages.get(2).getContent());

        assertEquals("assistant", messages.get(3).getRole());
        assertEquals("好的，为您查看茅台。", messages.get(3).getContent());

        assertEquals("user", messages.get(4).getRole());
        assertTrue(messages.get(4).getContent().contains("600519"));
        assertTrue(messages.get(4).getContent().contains("当前问题：茅台PE是多少？"));
    }
}
