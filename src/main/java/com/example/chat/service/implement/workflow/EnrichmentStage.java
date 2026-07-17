package com.example.chat.service.implement.workflow;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.EnrichmentResult;
import com.example.chat.common.dto.downstream.DpuRequest;
import com.example.chat.common.dto.downstream.LlmChunk;
import com.example.chat.common.dto.downstream.LlmRequest;
import com.example.chat.common.dto.downstream.RagRequest;
import com.example.chat.integration.client.DpuClient;
import com.example.chat.integration.client.LlmClient;
import com.example.chat.integration.client.RagClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流阶段：信息富化 (EnrichmentStage)
 * 并发调度 RAG 检索与 DPU 行情获取。
 * 遵循非阻塞响应式设计，RAG 结果到达时立即初始化并缓存追问热源，返回 Mono 结果封装。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class EnrichmentStage {

    private final RagClient ragClient;
    private final DpuClient dpuClient;
    private final LlmClient llmClient;

    public EnrichmentStage(RagClient ragClient, DpuClient dpuClient, LlmClient llmClient) {
        this.ragClient = ragClient;
        this.dpuClient = dpuClient;
        this.llmClient = llmClient;
    }

    /**
     * 并发富化流。当被订阅时，会同时向 RAG 与 DPU 投递请求。
     * RAG 结果返回后在 context 中初始化缓存追问热源，通过 Mono.zip 并发收集并返回 DTO 结果。
     */
    public Mono<EnrichmentResult> execute(ChatContext context) {
        log.info("开始并发构建 RAG 与 DPU 富化数据源, taskId={}", context.taskId());

        // 1. RAG 检索：并在返回后立即非阻塞地注册追问 Mono 热源
        Mono<String> ragMono = ragClient.retrieve(new RagRequest(context.getRewrittenQuestion(), context.sessionId(), 1, 5))
                .doOnNext(rag -> {
                    context.setRagData(rag);

                    // 构建追问 Mono 并在 context 中注册为 Cached 热源，等待后续阶段触发订阅
                    Mono<String> askMono = callGatewayAsk(context, context.getDatasetContent(), rag)
                            .doOnNext(ask -> context.setAskData(ask))
                            .share();
                    context.setAskMono(askMono);
                });

        // 2. DPU 行情检索
        Mono<String> dpuMono = dpuClient.query(new DpuRequest(context.getRewrittenQuestion(), true))
                .doOnNext(dpu -> context.setDpuData(dpu));

        return Mono.zip(ragMono, dpuMono)
                .map(tuple -> new EnrichmentResult(tuple.getT1(), tuple.getT2()));
    }

    /**
     * 链式非流式调用追问大模型服务，采用高性能 StringBuilder 收集拼接
     */
    private Mono<String> callGatewayAsk(ChatContext context, String dataset, String ragData) {
        Map<String, Object> promptParams = new HashMap<>();
        promptParams.put("question", context.getRewrittenQuestion());
        promptParams.put("ragdata", ragData);
        promptParams.put("content", dataset);

        String pageData = (String) context.getRequest().attributes().getOrDefault("pageData", "");
        promptParams.put("pageData", pageData);

        LlmRequest request = new LlmRequest("P052186", false, promptParams);

        return llmClient.stream(request, context.sessionId())
                .map(LlmChunk::text)
                .collect(StringBuilder::new, StringBuilder::append)
                .map(StringBuilder::toString)
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(ex -> {
                    log.error("追问模型调用超时或失败, 错误: {}", ex.getMessage());
                    return Mono.just("[]");
                });
    }
}
