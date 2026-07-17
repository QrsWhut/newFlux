package com.example.chat.service.implement.workflow;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatMessage;
import com.example.chat.common.dto.UiNode;
import com.example.chat.common.dto.downstream.MultiRewriteParam;
import com.example.chat.integration.client.RewriteClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流阶段：多轮对话改写 (RewriteStage)
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class RewriteStage {

    private final RewriteClient rewriteClient;

    public RewriteStage(RewriteClient rewriteClient) {
        this.rewriteClient = rewriteClient;
    }

    /**
     * 执行改写流程。如果历史记录为空，不执行改写；否则调用改写服务，并下发状态及改写内容。
     */
    public Flux<ChatEvent> execute(ChatContext context) {
        List<ChatMessage> history = context.getRequest().history();
        if (history == null || history.isEmpty()) {
            return Flux.empty();
        }

        // 构造改写参数
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            Map<String, String> item = new HashMap<>();
            item.put("role", msg.role());
            item.put("content", msg.content());
            messages.add(item);
        }
        
        // 把当前用户的问题也加入改写历史的末尾
        Map<String, String> current = new HashMap<>();
        current.put("role", "user");
        current.put("content", context.getRequest().question());
        messages.add(current);

        MultiRewriteParam param = new MultiRewriteParam(
                messages,
                context.userId(),
                "IntentNew",
                "2.0"
        );

        log.info("开始执行问句改写阶段, taskId={}", context.taskId());

        return rewriteClient.rewrite(param)
                .onErrorResume(err -> {
                    log.error("调用下游改写接口发生异常，降级使用原问句继续. 错误: {}", err.getMessage());
                    return Mono.just("");
                })
                .flatMapMany(rewritten -> {
                    if (StringUtils.hasText(rewritten)) {
                        log.info("问句改写成功, 原始问句: '{}', 改写后: '{}', taskId={}", 
                                context.getRequest().question(), rewritten, context.taskId());
                        context.setRewrittenQuestion(rewritten);
                        
                        // 同时发送状态更新与 UI 渲染节点给前端（改写后的增强问句卡片）
                        ChatEvent statusEvent = ChatEvent.status(context.taskId(), context.nextSequence(), "正在检索相关指标...");
                        UiNode uiNode = new UiNode(
                                "rewrite-card",
                                "rewriteQuestion",
                                Map.of("rewrittenQuestion", rewritten),
                                1L
                        );
                        ChatEvent uiEvent = ChatEvent.ui(context.taskId(), context.nextSequence(), uiNode);
                        return Flux.just(statusEvent, uiEvent);
                    }
                    log.warn("改写服务返回空或解析失败，将使用原问句继续, taskId={}", context.taskId());
                    return Flux.empty();
                });
    }
}
