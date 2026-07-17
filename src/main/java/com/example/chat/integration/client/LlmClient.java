package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.LlmChunk;
import com.example.chat.common.dto.downstream.LlmRequest;
import reactor.core.publisher.Flux;

/**
 * 流式大模型底层客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface LlmClient {

    /**
     * 发起流式模型请求，返回响应式流
     * 订阅由 Workflow 负责，Client 不得主动调用 subscribe() 或 block()
     *
     * @param request   大模型请求参数
     * @param sessionId 用户会话ID，用于透传和认证
     * @return 字符增量流
     */
    Flux<LlmChunk> stream(LlmRequest request, String sessionId);
}
