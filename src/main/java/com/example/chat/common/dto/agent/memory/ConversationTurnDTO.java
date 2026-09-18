package com.example.chat.common.dto.agent.memory;

import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationTurnStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 完整会话轮次不可变传输对象。
 *
 * @param id 主键
 * @param conversationId 所属会话主键
 * @param turnNo 会话内轮次号
 * @param taskId 单次请求幂等标识
 * @param userContent 用户显式输入
 * @param assistantContent 助手最终显式回答
 * @param status 轮次状态
 * @param compressionLevel 压缩等级
 * @param userTokenEstimate 用户输入估算 Token
 * @param assistantTokenEstimate 助手回答估算 Token
 * @param finalModelCallId 最终成功主模型调用主键
 * @param errorCode 脱敏错误码
 * @param errorMessage 脱敏错误摘要
 * @param finishTime 终态时间
 * @param toolCalls 按步骤与调用顺序排列的工具调用
 */
public record ConversationTurnDTO(
        Long id,
        Long conversationId,
        Integer turnNo,
        String taskId,
        String userContent,
        String assistantContent,
        ConversationTurnStatus status,
        CompressionLevel compressionLevel,
        Integer userTokenEstimate,
        Integer assistantTokenEstimate,
        Long finalModelCallId,
        String errorCode,
        String errorMessage,
        LocalDateTime finishTime,
        List<ConversationToolCallDTO> toolCalls) {

    /**
     * 复制工具调用列表，避免轮次快照被外部修改。
     */
    public ConversationTurnDTO {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
