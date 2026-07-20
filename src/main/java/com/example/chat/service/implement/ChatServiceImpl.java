package com.example.chat.service.implement;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.dto.EnrichmentResult;
import com.example.chat.common.dto.UiNode;
import com.example.chat.integration.client.DatasetClient;
import com.example.chat.service.interf.ChatService;
import com.example.chat.service.implement.workflow.EnrichmentStage;
import com.example.chat.service.implement.workflow.FirstAnswerStage;
import com.example.chat.service.implement.workflow.FollowupStage;
import com.example.chat.service.implement.workflow.RewriteStage;
import com.example.chat.service.implement.workflow.SecondAnswerStage;
import com.example.chat.stream.TextEventBatcher;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 响应式金融对话工作流编排核心实现类 (ChatServiceImpl)
 * 采用“并行计算，串行对齐输出”的 Reactor 原生时序编排。
 * 依靠 cached Mono 和哑流设计，在不引入手动订阅和 Long.MAX_VALUE 风险的前提下，
 * 达到最佳的并发吞吐量与纯净的串行卡片输出体验。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final RewriteStage rewriteStage;
    private final FirstAnswerStage firstAnswerStage;
    private final EnrichmentStage enrichmentStage;
    private final SecondAnswerStage secondAnswerStage;
    private final FollowupStage followupStage;
    private final DatasetClient datasetClient;

    public ChatServiceImpl(
            RewriteStage rewriteStage,
            FirstAnswerStage firstAnswerStage,
            EnrichmentStage enrichmentStage,
            SecondAnswerStage secondAnswerStage,
            FollowupStage followupStage,
            DatasetClient datasetClient) {
        this.rewriteStage = rewriteStage;
        this.firstAnswerStage = firstAnswerStage;
        this.enrichmentStage = enrichmentStage;
        this.secondAnswerStage = secondAnswerStage;
        this.followupStage = followupStage;
        this.datasetClient = datasetClient;
    }

    @Override
    public Flux<ChatEvent> stream(ChatRequest request) {
        return Flux.defer(() -> {
            ChatContext context = ChatContext.create(request);
            log.info("构建对话流响应式管道, taskId={}", context.taskId());

            // 1. 问句历史改写阶段
            Flux<ChatEvent> rewriteFlow = rewriteStage.execute(context);

            // 2. 串行拉取小作文数据集 -> 声明富化热源 -> 并行计算/串行输出
            Flux<ChatEvent> parallelFlow = datasetClient.fetchDataset(context.getRewrittenQuestion(), context.sessionId(), context.userId(), context.taskId())
                    .onErrorResume(ex -> {
                        if (ex instanceof com.example.chat.common.exception.DownstreamException dex && dex.isDegraded()) {
                            log.warn("拉取小作文失败，触发降级处理, error={}", dex.getMessage());
                            return Mono.just("");
                        }
                        return Mono.error(ex);
                    })
                    .flatMapMany(dataset -> {
                        context.setDatasetContent(dataset);

                        // 1. 声明并发冷富化流。仅在此处 triggerEnrichFlow 订阅一次拉起计算。
                        Mono<EnrichmentResult> enrichMono = enrichmentStage.execute(context);

                        // 2. 首段回答大模型流
                        Flux<ChatEvent> firstAnswerFlow = firstAnswerStage.execute(context);

                        // 3. 哑流：订阅并拉起富化冷流 (只被订阅一次)，计算结果在 doOnNext 中写入 context
                        Flux<ChatEvent> triggerEnrichFlow = enrichMono.thenMany(Flux.empty());

                        // 4. 并发拉起：大模型吐 Token 与 RAG/DPU 并发进行
                        Flux<ChatEvent> mergedFirstStep = Flux.merge(firstAnswerFlow, triggerEnrichFlow);

                        // 5. 串行卡片下发：合并流 complete 后，直接从 context 读取数据以固定物理顺序下发卡片事件
                        Flux<ChatEvent> emitEnrichFlow = Flux.defer(() -> {
                            ChatEvent ragCard = ChatEvent.ui(context.taskId(), context.nextSequence(), 
                                    new UiNode("rag-card", "ragData", Map.of("data", context.getRagData() != null ? context.getRagData() : ""), 1L));
                            ChatEvent dpuCard = ChatEvent.ui(context.taskId(), context.nextSequence(), 
                                    new UiNode("dpu-card", "dpuData", Map.of("data", context.getDpuData() != null ? context.getDpuData() : ""), 1L));
                            return Flux.just(ragCard, dpuCard);
                        });

                        return mergedFirstStep.concatWith(emitEnrichFlow);
                    });

            // 3. 编排主执行链路与第二阶段同步屏障
            Flux<ChatEvent> mainPipeline = rewriteFlow
                    .concatWith(parallelFlow)
                    .concatWith(Flux.defer(() -> {
                        // 屏障拦截点：此时首段 Token 及 RAG/DPU 富化已经全部 complete，context 信息均已对齐
                        if (!context.isShouldCallSecond()) {
                            return followupStage.execute(context);
                        }

                        Flux<ChatEvent> secondAnswerFlow = secondAnswerStage.execute(context);

                        // 获取追问 Mono 热源，并建立并行触发哑流
                        Mono<String> askMono = context.getAskMono();
                        Flux<ChatEvent> triggerAskFlow = (askMono != null ? askMono : Mono.just("[]"))
                                .thenMany(Flux.empty());

                        // 次轮模型与追问拉取并发执行，优先下发次段 Token
                        Flux<ChatEvent> mergedSecondStep = Flux.merge(secondAnswerFlow, triggerAskFlow);

                        return mergedSecondStep.concatWith(followupStage.execute(context));
                    }))
                    .concatWith(Flux.defer(() -> {
                        Map<String, Object> debugInfo = Map.of(
                            "rewrittenQuestion", context.getRewrittenQuestion() != null ? context.getRewrittenQuestion() : "",
                            "datasetContent", context.getDatasetContent() != null ? context.getDatasetContent() : "",
                            "ragData", context.getRagData() != null ? context.getRagData() : "",
                            "dpuData", context.getDpuData() != null ? context.getDpuData() : "",
                            "askData", context.getAskData() != null ? context.getAskData() : ""
                        );
                        ChatEvent debugEvent = ChatEvent.status(context.taskId(), context.nextSequence(), "DEBUG_DUMP:" + JSON.toJSONString(debugInfo));
                        return Flux.just(debugEvent, ChatEvent.complete(context.taskId(), context.nextSequence()));
                    }))
                    .onErrorResume(err -> {
                        // 遇到客户端取消或下游取消的异常，直接打印 info 日志并静默结束，不再下发 500 错误事件
                        if (err instanceof com.example.chat.common.exception.DownstreamException dex && dex.getErrorType() == com.example.chat.common.exception.DownstreamException.ErrorType.CANCELLED) {
                            log.info("AB1 对话流收到取消信号 (CANCELLED) 终止, taskId={}", context.taskId());
                            return Flux.empty();
                        }
                        log.error("AB1 对话流编排链路发生严重异常, taskId={}, error={}", 
                                context.taskId(), err.getMessage(), err);
                        // 主链路大模型发生异常时直接向前端发出 error 事件并熔断终止流，保证异常与完成信号互斥
                        return Flux.just(ChatEvent.error(context.taskId(), context.nextSequence(), "500", "AB1已停止回答。如有需要，请随时重新提问"));
                    });

            // 文本批处理器保证时序与 50ms 实时刷新率
            return TextEventBatcher.batch(mainPipeline);
        });
    }
}
