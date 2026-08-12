package com.example.chat.service.implement;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.enums.ExecutionMode;
import com.example.chat.service.interf.ChatExecutionService;
import com.example.chat.service.interf.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 对话执行模式路由器
 * 将 ChatRequest 按照 executionMode 路由至对应的 ChatExecutionService 执行器
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Primary
@Service
public class ChatExecutionRouter implements ChatService {

    private final Map<ExecutionMode, ChatExecutionService> serviceMap;

    public ChatExecutionRouter(List<ChatExecutionService> services) {
        Map<ExecutionMode, ChatExecutionService> map = new EnumMap<>(ExecutionMode.class);
        if (services != null) {
            for (ChatExecutionService service : services) {
                ExecutionMode mode = service.executionMode();
                if (mode == null) {
                    throw new IllegalStateException("ChatExecutionService 必须指定非空的 ExecutionMode");
                }
                if (map.containsKey(mode)) {
                    throw new IllegalStateException("重复注册 ExecutionMode 执行器: " + mode);
                }
                map.put(mode, service);
            }
        }
        this.serviceMap = Collections.unmodifiableMap(map);
        log.info("ChatExecutionRouter 初始化完成，注册模式: {}", serviceMap.keySet());
    }

    @Override
    public Flux<ChatEvent> stream(ChatRequest request) {
        if (request == null) {
            return Flux.error(new IllegalArgumentException("ChatRequest 不能为空"));
        }
        ExecutionMode mode = request.executionMode();
        if (mode == null) {
            mode = ExecutionMode.WORKFLOW;
        }

        ChatExecutionService service = serviceMap.get(mode);
        if (service == null) {
            log.error("未找到对应模式的执行器, mode={}, taskId={}", mode, request.taskId());
            return Flux.error(new IllegalArgumentException("未找到支持的对话模式执行器: " + mode));
        }

        log.info("路由请求至模式执行器: mode={}, taskId={}", mode, request.taskId());
        return service.stream(request);
    }
}
