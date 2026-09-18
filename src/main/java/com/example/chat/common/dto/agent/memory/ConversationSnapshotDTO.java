package com.example.chat.common.dto.agent.memory;

import com.example.chat.common.enums.ConversationContextStatus;
import com.example.chat.common.enums.ConversationStatus;

import java.util.List;

/**
 * 会话上下文不可变快照。
 *
 * @param conversationId 会话主键
 * @param userId 用户标识
 * @param sessionId 会话标识
 * @param status 会话状态
 * @param contextStatus 上下文治理状态
 * @param latestTurnNo 已分配最大轮次号
 * @param latestSummaryVersion 最新摘要版本
 * @param contextVersion 上下文语义版本
 * @param latestSummary 最新有效摘要
 * @param turns 摘要终点后的终态轮次
 */
public record ConversationSnapshotDTO(
        Long conversationId,
        String userId,
        String sessionId,
        ConversationStatus status,
        ConversationContextStatus contextStatus,
        Integer latestTurnNo,
        Integer latestSummaryVersion,
        Integer contextVersion,
        ConversationSummaryDTO latestSummary,
        List<ConversationTurnDTO> turns) {

    /**
     * 复制轮次列表，避免快照被外部修改。
     */
    public ConversationSnapshotDTO {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }
}
