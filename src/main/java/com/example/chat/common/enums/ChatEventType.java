package com.example.chat.common.enums;

/**
 * 业务事件类型定义
 * 用于 SSE 传输中区分不同的数据片和状态变化
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public enum ChatEventType {
    /**
     * 流式文本增量（包含大模型回答内容）
     */
    TEXT_DELTA,

    /**
     * 业务执行状态（如：正在改写、正在拉取小作文等）
     */
    STATUS,

    /**
     * UI 节点卡片更新（包含 RAG 数据卡片、DPU 卡片、NER实体列表、观点标识等）
     */
    UI_UPDATE,

    /**
     * 业务或系统错误
     */
    ERROR,

    /**
     * 对话流正常结束标识
     */
    COMPLETE,

    /**
     * 客户端取消标识
     */
    CANCELLED
}
