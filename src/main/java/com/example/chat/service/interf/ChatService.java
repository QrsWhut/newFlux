package com.example.chat.service.interf;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * 金融对话核心编排 Service 接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface ChatService {

    /**
     * 接收用户的对话请求，执行完整大模型与 RAG/DPU 链路的编排，并产生有序的事件流
     *
     * @param request 对话请求 DTO
     * @return 业务事件响应流
     */
    Flux<ChatEvent> stream(ChatRequest request);
}
