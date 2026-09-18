package com.example.chat.agent.memory;

import com.example.chat.common.dto.agent.memory.ConversationSummaryDTO;
import com.example.chat.common.dto.agent.memory.ConversationTurnDTO;
import com.example.chat.common.enums.CompressionLevel;

import java.util.List;

/**
 * 一次摘要压缩的不可变候选计划。
 *
 * @param compressionLevel 压缩等级
 * @param previousSummary 当前有效摘要
 * @param coveredStartTurnNo 新摘要覆盖起始轮次
 * @param coveredEndTurnNo 新摘要覆盖结束轮次
 * @param turns 本次新增纳入摘要的连续终态轮次
 * @param coverageAdvances 覆盖终点是否向前推进
 */
public record CompactionPlan(
        CompressionLevel compressionLevel,
        ConversationSummaryDTO previousSummary,
        Integer coveredStartTurnNo,
        Integer coveredEndTurnNo,
        List<ConversationTurnDTO> turns,
        boolean coverageAdvances) {

    /**
     * 校验覆盖范围并冻结候选轮次列表。
     */
    public CompactionPlan {
        if (compressionLevel == null || compressionLevel == CompressionLevel.NONE) {
            throw new IllegalArgumentException("压缩计划必须指定 NORMAL 或 DEEP");
        }
        if (coveredStartTurnNo == null || coveredStartTurnNo <= 0
                || coveredEndTurnNo == null || coveredEndTurnNo < coveredStartTurnNo) {
            throw new IllegalArgumentException("压缩计划覆盖范围不合法");
        }
        turns = turns == null ? List.of() : List.copyOf(turns);
        if (compressionLevel == CompressionLevel.NORMAL && turns.isEmpty()) {
            throw new IllegalArgumentException("普通压缩必须包含新增候选轮次");
        }
        if (compressionLevel == CompressionLevel.DEEP
                && turns.isEmpty() && previousSummary == null) {
            throw new IllegalArgumentException("深度压缩必须包含旧摘要或新增候选轮次");
        }
    }
}
