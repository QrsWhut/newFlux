package com.example.chat.agent.client;

import com.example.chat.agent.model.AgentModelEvent;
import com.example.chat.agent.model.AgentModelRequest;
import reactor.core.publisher.Flux;

/**
 * 多供应商 AI 模型路由接口。
 *
 * @author Codex
 * @since 2026-08-25
 */
public interface AiModelRouter extends AgentLlmClient {

    /**
     * 根据请求中的供应商和模型选择执行客户端。
     *
     * @param request 模型请求
     * @return 类型化模型事件流
     */
    Flux<AgentModelEvent> route(AgentModelRequest request);

    /**
     * 通过路由执行模型请求。
     *
     * @param request 模型请求
     * @return 类型化模型事件流
     */
    @Override
    default Flux<AgentModelEvent> respond(AgentModelRequest request) {
        return route(request);
    }
}
