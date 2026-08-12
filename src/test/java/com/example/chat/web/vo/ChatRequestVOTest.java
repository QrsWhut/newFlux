package com.example.chat.web.vo;

import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatRequestVO 模式解析与命令转换单元测试
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public class ChatRequestVOTest {

    @Test
    public void testDefaultExecutionModeIsWorkflow() {
        ChatRequestVO vo = new ChatRequestVO();
        vo.setSessionId("sess-001");
        vo.setUserId("user-001");
        vo.setQuestion("测试问题");

        ChatRequest request = vo.toCommand();
        assertEquals(ExecutionMode.WORKFLOW, request.executionMode());
        assertNotNull(request.history());
        assertTrue(request.history().isEmpty());
    }

    @Test
    public void testExplicitExecutionModeAgent() {
        ChatRequestVO vo = new ChatRequestVO();
        vo.setSessionId("sess-002");
        vo.setUserId("user-002");
        vo.setQuestion("测试 Agent");
        vo.setExecutionMode(ExecutionMode.AGENT);

        ChatRequest request = vo.toCommand();
        assertEquals(ExecutionMode.AGENT, request.executionMode());
    }

    @Test
    public void testDeprecatedModeMapping() {
        ChatRequestVO vo1 = new ChatRequestVO();
        vo1.setSessionId("sess-003");
        vo1.setUserId("user-003");
        vo1.setQuestion("测试旧版 1");
        vo1.setMode(1);
        assertEquals(ExecutionMode.WORKFLOW, vo1.toCommand().executionMode());

        ChatRequestVO vo2 = new ChatRequestVO();
        vo2.setSessionId("sess-004");
        vo2.setUserId("user-004");
        vo2.setQuestion("测试旧版 2");
        vo2.setMode(2);
        assertEquals(ExecutionMode.AGENT, vo2.toCommand().executionMode());
    }

    @Test
    public void testInvalidModeThrowsException() {
        ChatRequestVO vo = new ChatRequestVO();
        vo.setSessionId("sess-005");
        vo.setUserId("user-005");
        vo.setQuestion("非法模式");
        vo.setMode(99);

        assertThrows(IllegalArgumentException.class, vo::toCommand);
    }
}
