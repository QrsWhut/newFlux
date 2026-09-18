package com.example.chat.agent.trace;

import com.example.chat.common.enums.ModelCallStatus;

/**
 * 单次主模型调用的不可变事实快照。
 *
 * @param agentStepNo Agent 推理步骤号
 * @param attemptNo 当前步骤尝试序号
 * @param status 模型调用状态
 * @param providerRequestId 供应方响应标识
 * @param providerCode 实际供应方编码
 * @param routeId 实际路由标识
 * @param apiProtocol 模型调用协议
 * @param modelName 实际模型名称
 * @param estimatedInputTokens 调用前估算输入 Token
 * @param inputTokens 实际输入 Token
 * @param outputTokens 实际输出 Token
 * @param reasoningTokens 实际推理 Token
 * @param cachedInputTokens 实际缓存输入 Token
 * @param totalTokens 实际总 Token
 * @param contextWindow 模型上下文窗口
 * @param maxOutputTokens 最大输出 Token
 * @param anchorReusable usage 是否可作为增量估算锚点
 * @param contextFingerprint 上下文指纹
 * @param toolSchemaHash 实际工具定义哈希
 * @param latencyMillis 调用耗时毫秒数
 * @param errorCode 脱敏错误码
 * @param errorMessage 脱敏错误摘要
 * @param finalResponse 是否为最终显式回答对应调用
 */
public record ModelCallTraceSnapshot(
        int agentStepNo,
        int attemptNo,
        ModelCallStatus status,
        String providerRequestId,
        String providerCode,
        String routeId,
        String apiProtocol,
        String modelName,
        int estimatedInputTokens,
        Integer inputTokens,
        Integer outputTokens,
        Integer reasoningTokens,
        Integer cachedInputTokens,
        Integer totalTokens,
        int contextWindow,
        int maxOutputTokens,
        boolean anchorReusable,
        String contextFingerprint,
        String toolSchemaHash,
        Long latencyMillis,
        String errorCode,
        String errorMessage,
        boolean finalResponse) {
}
