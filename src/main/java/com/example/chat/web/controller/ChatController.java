package com.example.chat.web.controller;

import com.alibaba.fastjson.JSON;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.service.interf.ChatService;
import com.example.chat.task.TaskCancellationService;
import com.example.chat.web.vo.ChatRequestVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 金融对话流式处理 API 控制器
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final TaskCancellationService cancellationService;

    public ChatController(ChatService chatService, TaskCancellationService cancellationService) {
        this.chatService = chatService;
        this.cancellationService = cancellationService;
    }

    /**
     * 流式 SSE 返回金融对话事件流
     * 当客户端主动断开连接或者网络波动取消订阅时，会自动触发上游 LLM 和检索连接的及时熔断与注销
     *
     * @param requestVO 请求参数视图对象
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> stream(@Valid @RequestBody ChatRequestVO requestVO) {
        ChatRequest command = requestVO.toCommand();
        log.info("接收到流式对话请求, taskId={}, sessionId={}", command.taskId(), maskSessionId(command.sessionId()));

        return Flux.defer(() -> chatService.stream(command)
                .map(event -> ServerSentEvent.<ChatEvent>builder()
                        .event(event.type().name())
                        .id(Long.toString(event.sequence()))
                        .data(event)
                        .build())
                .doOnSubscribe(subscription -> {
                    // 将 Subscription 包装为 Disposable 注册到取消管理服务中，提供外部主动取消通道
                    cancellationService.register(command.taskId(), subscription::cancel);
                })
                .doFinally(signalType -> {
                    // 任务由于各种原因终结，移出取消句柄存储
                    cancellationService.remove(command.taskId());
                })
                .doOnCancel(() -> {
                    // 触发取消
                    cancellationService.cancel(command.taskId());
                })
        );
    }

    /**
     * 提供管理端或者外部直接中止指定任务的 API 接口
     *
     * @param taskId 任务唯一 ID
     * @return 操作结果
     */
    @PostMapping("/cancel")
    public String cancel(@RequestParam String taskId) {
        cancellationService.cancel(taskId);
        return "SUCCESS";
    }

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 6) + "***" + sessionId.substring(sessionId.length() - 2);
    }
}
