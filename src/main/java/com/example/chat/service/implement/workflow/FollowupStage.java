package com.example.chat.service.implement.workflow;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.UiNode;
import com.example.chat.integration.client.NerClient;
import com.example.chat.integration.client.ViewpointClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 工作流阶段：后续处理（FollowupStage）
 * 包括并行执行 NER 实体识别、观点校验，并非阻塞等待已富化的追问问题
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class FollowupStage {

    private final NerClient nerClient;
    private final ViewpointClient viewpointClient;

    public FollowupStage(NerClient nerClient, ViewpointClient viewpointClient) {
        this.nerClient = nerClient;
        this.viewpointClient = viewpointClient;
    }

    /**
     * 并行等待 NER、观点校验与追问结果，并下发对应的三合一 UI 卡片
     */
    public Flux<ChatEvent> execute(ChatContext context) {
        String fullAnswer = context.getFirstAnswer() + context.getSecondAnswer();

        // 1. 异步触发 NER
        Mono<String> nerMono = nerClient.extractEntities(fullAnswer, context.sessionId())
                .onErrorResume(err -> {
                    log.error("NER 实体提取异常, taskId={}, error={}", context.taskId(), err.getMessage());
                    return Mono.just("[]");
                });

        // 2. 异步触发观点校验
        Mono<String> viewpointMono = viewpointClient.checkViewpoint(
                context.getRewrittenQuestion(),
                fullAnswer,
                context.sessionId(),
                context.userId()
        )
        .onErrorResume(err -> {
            log.error("观点校验调用异常, taskId={}, error={}", context.taskId(), err.getMessage());
            return Mono.just("");
        });

        // 3. 非阻塞获取并等待追问结果 Mono
        Mono<String> askMono = context.getAskMono();
        if (askMono == null) {
            askMono = Mono.just("[]");
        }

        log.info("后续处理阶段：并发等待 NER、观点校验与追问结果..., taskId={}", context.taskId());

        return Mono.zip(nerMono, viewpointMono, askMono)
                .flatMapMany(tuple -> {
                    String nerResult = tuple.getT1();
                    String viewpointResult = tuple.getT2();
                    String askResult = tuple.getT3();

                    log.info("NER, 观点校验与追问全部返回就绪, 准备下发后续卡片, taskId={}", context.taskId());

                    // 将结果转换为 UI 卡片节点下发
                    UiNode nerNode = new UiNode("ner-card", "nerList", Map.of("data", nerResult), 1L);
                    UiNode viewpointNode = new UiNode("viewpoint-card", "viewpointFlag", Map.of("data", viewpointResult), 1L);
                    UiNode askNode = new UiNode("ask-card", "contentAsk", Map.of("data", askResult), 1L);

                    return Flux.just(
                            ChatEvent.ui(context.taskId(), context.nextSequence(), nerNode),
                            ChatEvent.ui(context.taskId(), context.nextSequence(), viewpointNode),
                            ChatEvent.ui(context.taskId(), context.nextSequence(), askNode)
                    );
                });
    }
}
