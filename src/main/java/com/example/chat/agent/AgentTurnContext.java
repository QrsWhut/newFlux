package com.example.chat.agent;

import com.example.chat.agent.memory.ConversationMemory;
import com.example.chat.agent.model.AgentMessage;
import com.example.chat.common.dto.ChatRequest;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单次 HTTP 请求轮次内的上下文与轨迹控制器
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
public class AgentTurnContext {

    private final ChatRequest request;
    private final ConversationMemory memory;

    @Builder.Default
    private List<AgentMessage> messages = new ArrayList<>();

    /** 当前用户轮次需要写入长期记忆的有序消息。 */
    @Builder.Default
    private List<AgentMessage> currentTurnMessages = new ArrayList<>();

    @Builder.Default
    private int stepCount = 0;

    /**
     * 已执行工具调用记录集合 (用于格式 "toolName:argumentsJson" 去重)
     */
    @Builder.Default
    private Set<String> executedToolSignatures = new HashSet<>();

    /**
     * 收集并累积模型的最终文本回答
     */
    @Builder.Default
    private StringBuilder fullAnswerBuilder = new StringBuilder();

    /**
     * 增加决策回合数
     */
    public void incrementStep() {
        this.stepCount++;
    }

    /**
     * 检查并记录工具签名，若已存在则返回 true (表示重复)
     */
    public boolean checkAndRecordToolCall(String toolName, String argumentsJson) {
        String sig = toolName + ":" + (argumentsJson != null ? argumentsJson.trim() : "");
        return !executedToolSignatures.add(sig);
    }
}
