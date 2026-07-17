package com.example.chat.common.dto;

import java.util.Map;

/**
 * 描述前端可渲染的一个业务节点（非 HTML/非前端组件）
 * 后端仅提供数据和对应的节点属性，由前端决定渲染卡片展示
 *
 * @param nodeId     节点唯一ID
 * @param nodeType   节点类型（如 RAG_DATA, DPU_DATA, NER_LIST, VIEWPOINT_FLAG, ASK_QUESTIONS）
 * @param properties 具体的属性键值对数据
 * @param version    数据版本戳，用于前端做卡片增量对比
 * @author Antigravity
 * @since 2026-07-17
 */
public record UiNode(
        String nodeId,
        String nodeType,
        Map<String, Object> properties,
        long version) {
}
