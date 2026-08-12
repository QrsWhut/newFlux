package com.example.chat.service.interf;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import reactor.core.publisher.Flux;

/**
 * 各种模式的对话执行器接口
 *
 * @author Antigravity
 * @since 2026-08-12
 */
public interface ChatExecutionService {

    /**
     * 获取支持的执行模式
     *
     * @return 执行模式枚举
     */
    ExecutionMode executionMode();

    /**
     * 执行对话请求，返回有序事件流
     *
     * @param request 对话请求 DTO
     * @return 业务事件响应流
     */
    Flux<ChatEvent> stream(ChatRequest request);
}
