package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.RagRequest;
import reactor.core.publisher.Mono;

/**
 * RAG 关联文档检索客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface RagClient {

    /**
     * 检索金融文档相关的 RAG 背景知识，并返回清洗过滤后的关键字段 JSON String
     *
     * @param request RAG 请求参数
     * @return 过滤后的关联文档 JSON 数组字符串（包含 title, content, publish_date）
     */
    Mono<String> retrieve(RagRequest request);
}
