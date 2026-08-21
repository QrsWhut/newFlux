package com.example.chat.agent.memory;

import com.example.chat.agent.model.AgentMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 单轮有序会话记录，保存用户消息、工具调用、工具状态和助手最终回答。
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurn {

    /** 按实际发生顺序保存的 OpenAI Chat Completions 消息。 */
    private List<AgentMessage> messages;
    /** 轮次完成时间。 */
    private Instant timestamp;

    /**
     * 获取本轮第一条用户消息。
     *
     * @return 用户问题，不存在时返回空字符串
     */
    public String getUserQuestion() {
        if (messages == null) {
            return "";
        }
        return messages.stream()
                .filter(message -> AgentMessage.ROLE_USER.equals(message.getRole()))
                .map(AgentMessage::getContent)
                .filter(content -> content != null && !content.isEmpty())
                .findFirst()
                .orElse("");
    }

    /**
     * 获取本轮最后一条助手文本消息。
     *
     * @return 助手最终回答，不存在时返回空字符串
     */
    public String getAssistantAnswer() {
        if (messages == null) {
            return "";
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentMessage message = messages.get(index);
            if (AgentMessage.ROLE_ASSISTANT.equals(message.getRole())
                    && message.getContent() != null && !message.getContent().isEmpty()) {
                return message.getContent();
            }
        }
        return "";
    }
}
