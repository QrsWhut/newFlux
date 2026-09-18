package com.example.chat.agent.trace;

import com.example.chat.common.enums.ToolCallStatus;

import java.time.LocalDateTime;

/**
 * 单次工具调用的不可变事实快照。
 *
 * @param agentStepNo Agent 推理步骤号
 * @param callNo 步骤内调用顺序号
 * @param callId 模型工具调用标识
 * @param toolName 工具名称
 * @param argumentsContent 脱敏并截断后的参数
 * @param status 工具调用状态
 * @param resultSummary 脱敏并截断后的结果摘要
 * @param resultReference 受控结果引用
 * @param errorCode 工具错误码
 * @param errorMessage 脱敏错误摘要
 * @param latencyMillis 调用耗时毫秒数
 * @param startTime 开始时间
 * @param finishTime 结束时间
 */
public record ToolCallTraceSnapshot(
        int agentStepNo,
        int callNo,
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
