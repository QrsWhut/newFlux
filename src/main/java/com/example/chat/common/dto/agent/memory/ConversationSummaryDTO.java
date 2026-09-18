package com.example.chat.common.dto.agent.memory;

import com.example.chat.common.enums.CompressionLevel;
import com.example.chat.common.enums.ConversationSummaryStatus;

/**
 * 会话摘要不可变传输对象。
 *
 * @param id 主键
 * @param conversationId 所属会话主键
 * @param summaryVersion 摘要版本
 * @param previousSummaryId 来源摘要主键
 * @param coveredStartTurnNo 覆盖起始轮次
 * @param coveredEndTurnNo 覆盖结束轮次
 * @param summaryContent 摘要正文
 * @param summaryTokenEstimate 摘要估算 Token
 * @param compressionLevel 压缩等级
 * @param modelCallId 压缩模型调用主键
 * @param providerCode 压缩模型供应方编码
 * @param routeId 压缩模型路由标识
 * @param apiProtocol 压缩模型协议
 * @param modelName 压缩模型名称
 * @param promptVersion 摘要提示词版本
 * @param promptHash 摘要提示词内容哈希
 * @param status 摘要状态
 */
public record ConversationSummaryDTO(
        Long id,
        Long conversationId,
        Integer summaryVersion,
        Long previousSummaryId,
        Integer coveredStartTurnNo,
        Integer coveredEndTurnNo,
        String summaryContent,
        Integer summaryTokenEstimate,
        CompressionLevel compressionLevel,
        Long modelCallId,
        String providerCode,
        String routeId,
        String apiProtocol,
        String modelName,
        String promptVersion,
        String promptHash,
        ConversationSummaryStatus status) {
}
