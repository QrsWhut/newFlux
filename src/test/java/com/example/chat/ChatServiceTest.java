package com.example.chat;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.ChatMessage;
import com.example.chat.common.dto.ChatRequest;
import com.example.chat.common.dto.downstream.LlmChunk;
import com.example.chat.common.enums.ChatEventType;
import com.example.chat.integration.client.*;
import com.example.chat.service.implement.ChatServiceImpl;
import com.example.chat.service.implement.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * ChatService 编排链路单元测试
 * 使用 StepVerifier 严格检验响应式流式事件的顺序、异常捕获与完成标志
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class ChatServiceTest {

    private LlmClient llmClient;
    private RagClient ragClient;
    private DpuClient dpuClient;
    private NerClient nerClient;
    private ViewpointClient viewpointClient;
    private RewriteClient rewriteClient;
    private DatasetClient datasetClient;

    private ChatServiceImpl chatService;
    private EnrichmentStage enrichmentStage;
    private FollowupStage followupStage;

    @BeforeEach
    public void setUp() {
        llmClient = Mockito.mock(LlmClient.class);
        ragClient = Mockito.mock(RagClient.class);
        dpuClient = Mockito.mock(DpuClient.class);
        nerClient = Mockito.mock(NerClient.class);
        viewpointClient = Mockito.mock(ViewpointClient.class);
        rewriteClient = Mockito.mock(RewriteClient.class);
        datasetClient = Mockito.mock(DatasetClient.class);

        // 默认返回 mock 小作文内容
        Mockito.when(datasetClient.fetchDataset(any(), any(), any(), any()))
                .thenReturn(Mono.just("MockDatasetContent"));

        // 初始化各个流程 Stage 组件 (EnrichmentStage 注入依赖 Client)
        RewriteStage rewriteStage = new RewriteStage(rewriteClient);
        FirstAnswerStage firstAnswerStage = new FirstAnswerStage(llmClient);
        enrichmentStage = new EnrichmentStage(ragClient, dpuClient, llmClient);
        SecondAnswerStage secondAnswerStage = new SecondAnswerStage(llmClient);
        followupStage = new FollowupStage(nerClient, viewpointClient);

        chatService = new ChatServiceImpl(
                rewriteStage,
                firstAnswerStage,
                enrichmentStage,
                secondAnswerStage,
                followupStage,
                datasetClient
        );
    }

    @Test
    public void testStreamFastMode_SkipSecondChat() {
        // 1. Mock 历史改写：返回空表示没有改写（直接用原句）
        Mockito.when(rewriteClient.rewrite(any())).thenReturn(Mono.just(""));
        
        // 2. Mock 首轮 LLM (P053102) 与 追问 LLM (P052186)
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P053102".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(
                        new LlmChunk("阿", ""),
                        new LlmChunk("里", ""),
                        new LlmChunk("#end#", "")
                ));
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(
                        new LlmChunk("[\"追问1\"]", "")
                ));

        // 3. Mock RAG 与 DPU
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("[\"RAG1\"]"));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_DATA_RESULT"));

        // 4. Mock NER、观点与追问
        Mockito.when(nerClient.extractEntities(anyString(), anyString())).thenReturn(Mono.just("[\"Entity1\"]"));
        Mockito.when(viewpointClient.checkViewpoint(anyString(), anyString(), anyString(), anyString())).thenReturn(Mono.just("1"));

        // 构造请求
        List<ChatMessage> history = new ArrayList<>();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("pageData", "page-test");
        ChatRequest request = new ChatRequest("test-task-1", "session-1", "user-1", "阿里巴巴战略?", history, attrs);

        // 执行流式调用，并使用 StepVerifier 校验事件顺序
        Flux<ChatEvent> resultFlux = chatService.stream(request);

        StepVerifier.create(resultFlux)
                // 1. 首段连续文本会被 TextEventBatcher 拼接为一个事件下发 (包括换行符)
                .expectNextMatches(event -> {
                    if (event.type() == ChatEventType.TEXT_DELTA && event.payload() instanceof ChatEvent.TextDelta delta) {
                        return "阿里\n".equals(delta.content());
                    }
                    return false;
                })
                // 3. RAG 卡片与 DPU 卡片
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                // 4. NER 实体、观点、以及追问（三者被 zip 在 followupStage 发送）
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "ner-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "viewpoint-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "ask-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                // 5. 完成
                .expectNextMatches(event -> event.type() == ChatEventType.COMPLETE)
                .verifyComplete();

        // 验证 RAG 和 DPU 物理上仅被拉取了一次，验证无多路复用重复请求
        Mockito.verify(ragClient, Mockito.times(1)).retrieve(any());
        Mockito.verify(dpuClient, Mockito.times(1)).query(any());
    }

    @Test
    public void testStreamFastMode_WithSecondChat() {
        // 1. Mock 历史改写
        Mockito.when(rewriteClient.rewrite(any())).thenReturn(Mono.just(""));
        
        // 2. Mock 首轮 (P053102)、次轮 (P052184) 与 追问 (P052186)
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P053102".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(
                        new LlmChunk("百", ""),
                        new LlmChunk("度", ""),
                        new LlmChunk("{#}", "")
                ));
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P052184".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(
                        new LlmChunk("搜", ""),
                        new LlmChunk("索", "")
                ));
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(
                        new LlmChunk("[\"追问2\"]", "")
                ));

        // 3. Mock RAG 与 DPU
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("[\"RAG1\"]"));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_DATA_RESULT"));

        // 4. Mock NER、观点与追问
        Mockito.when(nerClient.extractEntities(anyString(), anyString())).thenReturn(Mono.just("[\"Entity2\"]"));
        Mockito.when(viewpointClient.checkViewpoint(anyString(), anyString(), anyString(), anyString())).thenReturn(Mono.just("0"));

        // 构造请求
        List<ChatMessage> history = new ArrayList<>();
        Map<String, Object> attrs = new HashMap<>();
        ChatRequest request = new ChatRequest("test-task-2", "session-2", "user-2", "百度战略?", history, attrs);

        // 执行流式调用
        Flux<ChatEvent> resultFlux = chatService.stream(request);

        StepVerifier.create(resultFlux)
                // 首段合并文本 (包含换行)
                .expectNextMatches(event -> "百度\n".equals(((ChatEvent.TextDelta) event.payload()).content()))
                // RAG & DPU UI
                .expectNextMatches(event -> "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                // 次段合并文本 (包含换行)
                .expectNextMatches(event -> "搜索\n".equals(((ChatEvent.TextDelta) event.payload()).content()))
                // NER & viewpoint & ask UI
                .expectNextMatches(event -> "ner-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> "viewpoint-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> "ask-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                // 完成
                .expectNextMatches(event -> event.type() == ChatEventType.COMPLETE)
                .verifyComplete();

        // 验证进入二轮时，首轮富化的 RAG 与 DPU 物理上仅被拉取了一次
        Mockito.verify(ragClient, Mockito.times(1)).retrieve(any());
        Mockito.verify(dpuClient, Mockito.times(1)).query(any());
    }

    @Test
    public void testEnrichmentOrderAndResilience_DpuFirst() {
        setupBaseMocks();
        // DPU 立即返回，RAG 延迟 30ms 返回，验证输出顺序仍是 RAG -> DPU
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_FAST"));
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("RAG_SLOW").delayElement(Duration.ofMillis(30)));

        List<ChatMessage> history = new ArrayList<>();
        ChatRequest request = new ChatRequest("task-order-1", "sess-order-1", "user-1", "提问", history, Map.of());
        Flux<ChatEvent> res = chatService.stream(request);

        StepVerifier.create(res)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .thenCancel()
                .verify();
    }

    @Test
    public void testEnrichmentOrderAndResilience_RagFirst() {
        setupBaseMocks();
        // RAG 立即返回，DPU 延迟 30ms 返回，验证输出顺序仍是 RAG -> DPU
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("RAG_FAST"));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_SLOW").delayElement(Duration.ofMillis(30)));

        List<ChatMessage> history = new ArrayList<>();
        ChatRequest request = new ChatRequest("task-order-2", "sess-order-2", "user-2", "提问", history, Map.of());
        Flux<ChatEvent> res = chatService.stream(request);

        StepVerifier.create(res)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .thenCancel()
                .verify();
    }

    @Test
    public void testEnrichmentOrderAndResilience_RagFailure() {
        setupBaseMocks();
        // RAG 发生 degraded 降级错误，DPU 正常返回。验证流仍能走完并输出空的 RAG 卡片
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.error(
                new DownstreamException("RAG", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "RAG崩了")
        ));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_NORMAL"));

        List<ChatMessage> history = new ArrayList<>();
        ChatRequest request = new ChatRequest("task-order-3", "sess-order-3", "user-3", "提问", history, Map.of());
        Flux<ChatEvent> res = chatService.stream(request);

        StepVerifier.create(res)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .expectNextMatches(event -> {
                    if (event.type() == ChatEventType.UI_UPDATE && "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId())) {
                        String data = (String) ((com.example.chat.common.dto.UiNode) event.payload()).properties().get("data");
                        return "".equals(data); // 降级为空结果卡片
                    }
                    return false;
                })
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .thenCancel()
                .verify();
    }

    @Test
    public void testEnrichmentOrderAndResilience_DpuFailure() {
        setupBaseMocks();
        // DPU 发生 degraded 降级错误，RAG 正常返回。验证流程不受影响并输出空的 DPU 卡片
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.error(
                new DownstreamException("DPU", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "DPU崩了")
        ));
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("RAG_NORMAL"));

        List<ChatMessage> history = new ArrayList<>();
        ChatRequest request = new ChatRequest("task-order-4", "sess-order-4", "user-4", "提问", history, Map.of());
        Flux<ChatEvent> res = chatService.stream(request);

        StepVerifier.create(res)
                .expectNextMatches(event -> event.type() == ChatEventType.TEXT_DELTA)
                .expectNextMatches(event -> event.type() == ChatEventType.UI_UPDATE && "rag-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId()))
                .expectNextMatches(event -> {
                    if (event.type() == ChatEventType.UI_UPDATE && "dpu-card".equals(((com.example.chat.common.dto.UiNode) event.payload()).nodeId())) {
                        String data = (String) ((com.example.chat.common.dto.UiNode) event.payload()).properties().get("data");
                        return "".equals(data); // 降级为空结果卡片
                    }
                    return false;
                })
                .thenCancel()
                .verify();
    }

    @Test
    public void testAskRequestCancellationAndSingleEmission() {
        setupBaseMocks();
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("[\"RAG_CANCEL_DATA\"]"));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_CANCEL_DATA"));
        AtomicBoolean askCancelled = new AtomicBoolean(false);

        // Mock 追问大模型流为 never() 并监听 cancel 信号
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.<LlmChunk>just(new LlmChunk("追问文本", ""))
                        .concatWith(Flux.never()) // 保证不自动完成以等待取消
                        .doOnCancel(() -> askCancelled.set(true))
                );

        com.example.chat.common.dto.ChatContext context = com.example.chat.common.dto.ChatContext.create(
                new ChatRequest("task-cancel-ask", "sess-cancel-ask", "user-cancel-ask", "提问", new ArrayList<>(), Map.of())
        );
        context.setRewrittenQuestion("改写提问");
        context.setDatasetContent("数据集");

        // 1. 订阅 enrichMono 触发 RAG 数据返回并初始化 context 里的 askMono
        StepVerifier.create(enrichmentStage.execute(context))
                .expectNextCount(1)
                .verifyComplete();

        // 2. 此时 context 里的 askMono 已初始化。直接调用并订阅 followupStage.execute 物理链条
        Flux<ChatEvent> followupFlow = followupStage.execute(context);

        StepVerifier.create(followupFlow)
                .thenRequest(1) // 触发对 askMono 的实际订阅 (因为是 share，在首次被 followupStage 订阅时拉起)
                .thenAwait(Duration.ofMillis(100)) // 微等 100ms 保证异步订阅关系彻底建立完毕
                .thenCancel()   // 物理取消订阅，cancel 信号逆向透传至 askMono 的 WebClient 底层
                .verify();

        // 验证追问的 HTTP 异步连接在客户端取消时能物理逆向传播取消
        assertTrue(askCancelled.get(), "客户端取消时，后台追问大模型请求必须能够物理取消");

        // 验证追问只被调用了一次
        Mockito.verify(llmClient, Mockito.times(1))
                .stream(Mockito.argThat(req -> req != null && "P052186".equals(req.promptId())), anyString());
    }

    @Test
    public void testEnrichmentIsolation_RagSuccessDpuFailure() {
        setupBaseMocks();
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("[\"RAG_SUCCESS\"]"));
        // DPU 发生可降级的故障
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.error(
                new DownstreamException("DPU", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "DPU崩了")
        ));

        com.example.chat.common.dto.ChatContext context = com.example.chat.common.dto.ChatContext.create(
                new ChatRequest("task-isolation-1", "sess-1", "user-1", "提问", new ArrayList<>(), Map.of())
        );
        context.setRewrittenQuestion("改写提问");

        StepVerifier.create(enrichmentStage.execute(context))
                .expectNextMatches(res -> "[\"RAG_SUCCESS\"]".equals(res.ragData()) && "".equals(res.dpuData()))
                .verifyComplete();
    }

    @Test
    public void testEnrichmentIsolation_DpuSuccessRagFailure() {
        setupBaseMocks();
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_SUCCESS"));
        // RAG 发生可降级的故障
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.error(
                new DownstreamException("RAG", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "RAG崩了")
        ));

        com.example.chat.common.dto.ChatContext context = com.example.chat.common.dto.ChatContext.create(
                new ChatRequest("task-isolation-2", "sess-2", "user-2", "提问", new ArrayList<>(), Map.of())
        );
        context.setRewrittenQuestion("改写提问");

        StepVerifier.create(enrichmentStage.execute(context))
                .expectNextMatches(res -> "".equals(res.ragData()) && "DPU_SUCCESS".equals(res.dpuData()))
                .verifyComplete();
    }

    @Test
    public void testEnrichmentIsolation_BothFailure() {
        setupBaseMocks();
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.error(
                new DownstreamException("RAG", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "RAG崩了")
        ));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.error(
                new DownstreamException("DPU", 500, DownstreamException.ErrorType.HTTP_SERVER_ERROR, false, true, "DPU崩了")
        ));

        com.example.chat.common.dto.ChatContext context = com.example.chat.common.dto.ChatContext.create(
                new ChatRequest("task-isolation-3", "sess-3", "user-3", "提问", new ArrayList<>(), Map.of())
        );
        context.setRewrittenQuestion("改写提问");

        StepVerifier.create(enrichmentStage.execute(context))
                .expectNextMatches(res -> "".equals(res.ragData()) && "".equals(res.dpuData()))
                .verifyComplete();
    }

    @Test
    public void testEnrichmentIsolation_CancellationNoFallback() {
        setupBaseMocks();
        // RAG 返回取消信号（ degraded=false, CANCELLED ）
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.error(
                new DownstreamException("RAG", 499, DownstreamException.ErrorType.CANCELLED, false, false, "取消了")
        ));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_SUCCESS"));

        com.example.chat.common.dto.ChatContext context = com.example.chat.common.dto.ChatContext.create(
                new ChatRequest("task-isolation-4", "sess-4", "user-4", "提问", new ArrayList<>(), Map.of())
        );
        context.setRewrittenQuestion("改写提问");

        // 验证取消信号（不可降级）继续向外传播，不被转换成空字符串
        StepVerifier.create(enrichmentStage.execute(context))
                .expectErrorMatches(ex -> ex instanceof DownstreamException dex && dex.getErrorType() == DownstreamException.ErrorType.CANCELLED)
                .verify();
    }

    @Test
    public void testStateMutualExclusion_LlmErrorNoComplete() {
        setupBaseMocks();
        Mockito.when(ragClient.retrieve(any())).thenReturn(Mono.just("RAG_OK"));
        Mockito.when(dpuClient.query(any())).thenReturn(Mono.just("DPU_OK"));
        // LLM 返回严重错误而熔断
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && !"P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.error(new RuntimeException("LLM崩了")));

        ChatRequest request = new ChatRequest("task-ex-1", "sess-ex-1", "user-ex-1", "提问", new ArrayList<>(), Map.of());
        Flux<ChatEvent> res = chatService.stream(request);

        // 期待只输出一个 ERROR 事件，且无任何 COMPLETE 事件
        StepVerifier.create(res)
                .expectNextMatches(event -> event.type() == ChatEventType.ERROR)
                .expectComplete()
                .verify();
    }

    private void setupBaseMocks() {
        Mockito.when(rewriteClient.rewrite(any())).thenReturn(Mono.just(""));
        Mockito.when(datasetClient.fetchDataset(any(), any(), any(), any())).thenReturn(Mono.just(""));
        // Mock 普通大模型首轮与次轮
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && !"P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(new LlmChunk("答", "")));
        // Mock 追问大模型返回默认数据，防止常规测试中调用该服务报 NullPointerException
        Mockito.when(llmClient.stream(Mockito.argThat(req -> req != null && "P052186".equals(req.promptId())), anyString()))
                .thenReturn(Flux.just(new LlmChunk("[\"追问\"]", "")));
        Mockito.when(nerClient.extractEntities(anyString(), anyString())).thenReturn(Mono.just("[]"));
        Mockito.when(viewpointClient.checkViewpoint(anyString(), anyString(), anyString(), anyString())).thenReturn(Mono.just("0"));
    }
}
