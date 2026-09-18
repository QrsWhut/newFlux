package com.example.chat.common.dto.agent.memory;

import com.example.chat.common.enums.ToolCallStatus;

import java.time.LocalDateTime;

/**
 * 会话工具调用不可变传输对象。
 *
 * @param id 主键
 * @param turnId 所属轮次主键
 * @param agentStepNo Agent 推理步骤号
 * @param callNo 步骤内调用顺序号
 * @param callId 模型工具调用标识
 * @param toolName 工具名称
 * @param argumentsContent 脱敏参数字符串
 * @param status 工具调用状态
 * @param resultSummary 可持久化结果摘要
 * @param resultReference 受控结果引用
 * @param errorCode 工具错误码
 * @param errorMessage 脱敏错误摘要
 * @param latencyMillis 调用耗时毫秒数
 * @param startTime 开始时间
 * @param finishTime 结束时间
 */
public record ConversationToolCallDTO(
        Long id,
        Long turnId,
        Integer agentStepNo,
        Integer callNo,
        String callId,
        String toolName,
        String argumentsContent,
        ToolCallStatus status,
        String resultSummary,
        String resultReference,
        String errorCode,
        String errorMessage,
        Long latencyMillis,
        LocalDateTime startTime,
        LocalDateTime finishTime) {
}
