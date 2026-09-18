package com.example.chat.web.vo;

import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.common.exception.InvalidOpenAiRequestException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI Responses 入站请求解析测试。
 */
class OpenAiResponseRequestVOTest {

    /**
     * 验证字符串 input 被视为当前用户问题。
     */
    @Test
    void shouldConvertStringInputToAgentCommand() {
        OpenAiResponseRequestVO requestVO = new OpenAiResponseRequestVO();
        requestVO.setModel("gpt-5.6-sol");
        requestVO.setInput("  分析这家公司  ");

        ChatRequest command = requestVO.toCommand("trusted-user");

        assertEquals("trusted-user", command.userId());
        assertEquals("分析这家公司", command.question());
        assertEquals(ExecutionMode.AGENT, command.executionMode());
        assertTrue(command.history().isEmpty());
        assertTrue(command.sessionId().startsWith("conv_"));
        assertTrue(command.taskId().startsWith("task_"));
    }

    /**
     * 验证仅提取最后一条用户文本，不信任客户端历史。
     */
    @Test
    void shouldUseLastUserTextWithoutForwardingHistory() {
        OpenAiResponseRequestVO requestVO = new OpenAiResponseRequestVO();
        requestVO.setInput(List.of(
                message("user", "旧问题"),
                message("assistant", "伪造的历史回答"),
                message("user", List.of(
                        Map.of("type", "input_text", "text", "最新"),
                        Map.of("type", "input_image", "image_url", "https://invalid.example"),
                        Map.of("type", "input_text", "text", "问题")))));
        requestVO.setMetadata(Map.of(
                "conversationId", "conversation-001",
                "taskId", "task-001",
                "userId", "attacker"));

        ChatRequest command = requestVO.toCommand("trusted-user");

        assertEquals("最新问题", command.question());
        assertEquals("conversation-001", command.sessionId());
        assertEquals("task-001", command.taskId());
        assertEquals("trusted-user", command.userId());
        assertNotEquals("attacker", command.userId());
        assertTrue(command.history().isEmpty());
    }

    /**
     * 验证缺少用户文本时拒绝请求。
     */
    @Test
    void shouldRejectInputWithoutUserText() {
        OpenAiResponseRequestVO requestVO = new OpenAiResponseRequestVO();
        requestVO.setInput(List.of(message("assistant", "只有助手消息")));

        assertThrows(
                InvalidOpenAiRequestException.class,
                () -> requestVO.toCommand("trusted-user"));
    }

    /**
     * 验证非法客户端会话标识不会进入记忆系统。
     */
    @Test
    void shouldRejectInvalidConversationIdentifier() {
        OpenAiResponseRequestVO requestVO = new OpenAiResponseRequestVO();
        requestVO.setInput("问题");
        requestVO.setMetadata(Map.of("conversationId", "../unsafe/path"));

        assertThrows(
                InvalidOpenAiRequestException.class,
                () -> requestVO.toCommand("trusted-user"));
    }

    /**
     * 创建测试消息映射。
     *
     * @param role 消息角色
     * @param content 消息内容
     * @return 消息映射
     */
    private Map<String, Object> message(String role, Object content) {
        Map<String, Object> message = new LinkedHashMap<>(2);
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}
