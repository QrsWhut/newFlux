# HTTP 对话流式服务编码说明书

> 本文用于交给其他 AI 或开发人员执行。除非特别说明，所有代码示例均为 Java 21、Spring Boot 3.x、Spring WebFlux 项目代码。

## 1. 开发目标与强制边界

### 1.1 目标

在空项目中实现一个仅提供 HTTP 接口的金融对话流式服务：

```text
HTTP 请求
  -> 问句改写
  -> 首轮大模型回答
  -> 按模型判断并行调用 RAG / DPU
  -> 二轮大模型回答
  -> 追问生成
  -> HTTP SSE 流式输出
```

内部业务返回 `Flux<ChatEvent>`，HTTP 层再转换为 SSE。不得让业务层依赖 `SseEmitter`、`ServerSentEvent` 或 `ServerHttpResponse`。

### 1.2 强制技术边界

- JDK 21。
- Spring WebFlux + Reactor Netty。
- 下游 HTTP 统一使用 `WebClient`。
- 业务编排使用 `Mono`、`Flux`、`flatMap`、`zip`、`concat`。
- 禁止在响应式主链路调用 `block()`、`get()`、`join()`、`await()`。
- 禁止在 Client、Service、Workflow 内主动调用 `subscribe()`。
- A2A SDK 不进入本项目第一阶段；后续只能新增适配器。
- 使用 FastJSON 序列化事件（若项目已有统一 JSON 组件，则保持项目约定，不在多个组件之间混用）。
- 所有类、方法和关键设计必须有中文 Javadoc；注释解释“为什么这样设计”，而不是重复代码含义。

## 2. 项目初始化

### 2.1 Maven 依赖

创建 `pom.xml`，至少包含：

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.11</spring-boot.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

不要同时引入 `spring-boot-starter-web`，否则会把项目带回 Spring MVC 运行时。若必须兼容旧同步 SDK，使用 JDK 21 虚拟线程或独立阻塞适配器，不要因此引入 MVC。

### 2.2 目录结构

```text
src/main/java/com/example/chat
├── ChatApplication.java
├── common
│   ├── dto
│   │   ├── ChatRequest.java
│   │   ├── ChatContext.java
│   │   ├── ChatEvent.java
│   │   ├── ChatResult.java
│   │   ├── UiNode.java
│   │   └── downstream
│   ├── enums
│   └── exception
├── config
│   ├── WebClientConfig.java
│   ├── DownstreamProperties.java
│   ├── VirtualThreadConfig.java
│   └── JacksonOrFastJsonConfig.java
├── integration
│   ├── client
│   │   ├── LlmClient.java
│   │   ├── WebClientLlmClient.java
│   │   ├── RewriteClient.java
│   │   ├── RagClient.java
│   │   └── DpuClient.java
│   └── blocking
│       └── LegacyBlockingGateway.java
├── service
│   ├── interf
│   │   └── ChatService.java
│   └── implement
│       ├── ChatServiceImpl.java
│       └── workflow
│           ├── RewriteStage.java
│           ├── FirstAnswerStage.java
│           ├── EnrichmentStage.java
│           ├── SecondAnswerStage.java
│           └── FollowupStage.java
├── stream
│   ├── ChatEventAssembler.java
│   └── TextEventBatcher.java
├── task
│   ├── ChatTask.java
│   ├── TaskStateStore.java
│   └── TaskCancellationService.java
├── observability
└── web
    ├── controller
    │   └── ChatController.java
    └── vo
        ├── ChatRequestVO.java
        └── ChatEventVO.java
```

依赖方向必须保持：

```text
web -> service -> integration
common 被各层依赖，但 common 不依赖 web
integration 不依赖 service
```

## 3. 核心数据模型

### 3.1 ChatRequest

```java
/**
 * 对话请求。该对象只描述业务输入，不携带 HTTP Response 或线程对象。
 */
public record ChatRequest(
        String taskId,
        String sessionId,
        String userId,
        String question,
        List<ChatMessage> history,
        Map<String, Object> attributes) {
}
```

要求：Controller 将 `ChatRequestVO` 转换成 `ChatRequest` 后再进入 Service；Service 不接受 Web 层 VO。

### 3.2 ChatEvent

```java
/**
 * 内部统一事件。它不依赖 HTTP，因此未来可被 HTTP、A2A 或消息队列复用。
 */
public record ChatEvent(
        String taskId,
        long sequence,
        ChatEventType type,
        Instant timestamp,
        Object payload,
        boolean terminal) {

    public static ChatEvent text(String taskId, long sequence, String text) {
        return new ChatEvent(taskId, sequence, ChatEventType.TEXT_DELTA,
                Instant.now(), new TextDelta(text), false);
    }
}
```

`ChatEventType` 至少包含：`TEXT_DELTA`、`STATUS`、`UI_UPDATE`、`ERROR`、`COMPLETE`、`CANCELLED`。

### 3.3 UINode

UINode 是业务 UI 状态，不是 A2A 专属对象：

```java
/**
 * 描述前端可渲染的一个节点。后端只维护状态和增量补丁，不负责具体页面布局。
 */
public record UiNode(
        String nodeId,
        String nodeType,
        Map<String, Object> properties,
        long version) {
}
```

前端根据 `UI_UPDATE` 事件更新节点。后端不得在事件中夹带 HTML 或前端组件实例。

## 4. WebClient 配置

### 4.1 配置属性

创建 `DownstreamProperties`，配置每类下游独立参数：

```yaml
downstream:
  llm:
    base-url: http://llm-service
    max-connections: 300
    pending-acquire-max-count: 500
    connect-timeout: 2s
    response-timeout: 60s
  rag:
    base-url: http://rag-service
    max-connections: 100
    response-timeout: 10s
  dpu:
    base-url: http://dpu-service
    max-connections: 100
    response-timeout: 10s
```

连接数不是 QPS。它只限制本服务同时占用的下游连接，实际值必须通过压测和下游限流共同确定。

### 4.2 WebClient Bean

每个下游建议拥有独立 `ConnectionProvider`，避免 LLM 长连接占满 RAG/DPU：

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient(DownstreamProperties properties) {
        ConnectionProvider provider = ConnectionProvider.builder("llm-pool")
                .maxConnections(properties.llm().maxConnections())
                .pendingAcquireMaxCount(properties.llm().pendingAcquireMaxCount())
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(properties.llm().responseTimeout())
                .compress(true);

        return WebClient.builder()
                .baseUrl(properties.llm().baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();
    }
}
```

不要在每次请求中创建 `WebClient`、`HttpClient` 或 `ConnectionProvider`，否则连接池无法复用并可能造成资源泄漏。

## 5. 下游 Client 编码规范

### 5.1 LLM 流式 Client

```java
public interface LlmClient {

    /**
     * 发起一次流式模型请求。订阅由 Workflow 负责，Client 不得主动订阅。
     */
    Flux<LlmChunk> stream(LlmRequest request);
}
```

实现要点：

```java
@Component
public class WebClientLlmClient implements LlmClient {

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        return webClient.post()
                .uri("/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toDownstreamError)
                .bodyToFlux(String.class)
                .map(this::parseChunk)
                .timeout(Duration.ofSeconds(60))
                .doOnCancel(() -> log.info("LLM请求被取消, requestId={}", request.requestId()))
                .doFinally(signal -> recordFinish(request, signal));
    }
}
```

禁止在此方法中调用 `subscribe()`。禁止把 `Flux` 转换成 `CompletableFuture` 后等待结果。

### 5.2 非流式 Client

```java
public interface RagClient {

    /**
     * 查询检索结果。网络等待由 Reactor Netty 管理，不占用专属阻塞线程。
     */
    Mono<RagResult> retrieve(RagRequest request);
}
```

RAG、DPU、行情数据互不依赖时，在 Workflow 中：

```java
Mono<EnrichmentData> enrichment = Mono.zip(
        ragClient.retrieve(ragRequest),
        dpuClient.query(dpuRequest),
        marketClient.query(marketRequest))
    .map(tuple -> merge(tuple.getT1(), tuple.getT2(), tuple.getT3()));
```

每个 Client 只负责一个下游协议；不得在 Client 内部决定是否进入二轮回答。

## 6. Workflow 编码方式

### 6.1 Service 接口

```java
public interface ChatService {

    /**
     * 执行完整对话并产生有序事件流。
     */
    Flux<ChatEvent> stream(ChatRequest request);
}
```

### 6.2 阶段编排

建议使用阶段类拆分，而不是把所有逻辑写在一个方法：

```java
public Flux<ChatEvent> stream(ChatRequest request) {
    ChatContext context = ChatContext.create(request);

    return rewriteStage.execute(context)
            .thenMany(firstAnswerStage.execute(context))
            .concatWith(Mono.defer(() -> {
                if (!context.needEnrichment()) {
                    return followupStage.execute(context);
                }
                return enrichmentStage.execute(context)
                        .thenMany(secondAnswerStage.execute(context))
                        .concatWith(followupStage.execute(context));
            }))
            .concatWith(Mono.defer(() -> Flux.just(
                    ChatEvent.complete(context.taskId(), context.nextSequence()))))
            .onErrorResume(error -> Flux.just(
                    ChatEvent.error(context.taskId(), context.nextSequence(), error)));
}
```

实现时要注意：如果某个阶段本身产生多个事件，应使用 `concatMap` 或 `concatWith` 保证阶段顺序；不能使用无序的 `flatMap` 把状态事件打乱。

### 6.3 首轮 LLM 流转换

```java
Flux<ChatEvent> firstAnswer = llmClient.stream(request)
        .map(LlmChunk::text)
        .filter(StringUtils::hasText)
        .map(text -> context.appendAnswer(text))
        .map(text -> ChatEvent.text(context.taskId(), context.nextSequence(), text));
```

实际项目中应由 `ChatEventAssembler` 负责合并，不建议每个 Token 都构造一次完整 UI 对象。

## 7. 事件合并实现

### 7.1 文本合并

```java
public Flux<ChatEvent> batchText(Flux<ChatEvent> source) {
    return source
            .bufferTimeout(32, Duration.ofMillis(50))
            .filter(batch -> !batch.isEmpty())
            .map(this::mergeTextBatch);
}
```

注意：`bufferTimeout` 只适用于连续文本批次。状态、错误、完成和取消事件不能无条件和文本一起合并。

推荐做法是先按事件边界分段：

```text
TEXT_DELTA 连续区间 -> bufferTimeout -> 合并
STATUS/UI_UPDATE     -> 原顺序发送
ERROR/COMPLETE       -> 先 flush 文本，再立即发送
```

### 7.2 不使用 `onBackpressureLatest` 丢 Token

大模型 Token 是增量数据，丢失一个 Token 就可能破坏最终文本。因此：

- 原始 Token 不使用 `onBackpressureLatest()`。
- 文本先拼接为可恢复的完整快照，再允许合并中间快照。
- 如果必须设置边界，必须明确记录丢弃数量，并保证客户端可以通过完整快照恢复。

## 8. Controller 编码方式

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequestVO request) {
        ChatRequest command = request.toCommand();

        return chatService.stream(command)
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.type().name())
                        .id(Long.toString(event.sequence()))
                        .data(FastJson.toJson(event))
                        .build())
                .doOnCancel(() -> cancellationService.cancel(command.taskId()));
    }
}
```

Controller 只做四件事：参数校验、构造命令、调用 Service、转换 SSE。不得在 Controller 中调用下游接口或编排 RAG/DPU。

响应头由 WebFlux 或 `WebFilter` 统一设置：

```text
Cache-Control: no-cache
X-Accel-Buffering: no
```

## 9. 虚拟线程使用方式

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

注意：启用配置不等于每个自定义 Executor 都会自动变为虚拟线程。遗留同步 SDK 使用明确的适配器：

```java
public Mono<LegacyResult> callLegacy(LegacyRequest request) {
    return Mono.fromCallable(() -> legacySdk.call(request))
            .subscribeOn(blockingScheduler);
}
```

`blockingScheduler` 可以由虚拟线程执行器提供，但必须设置业务并发边界。虚拟线程不是无限容量，也不能绕过下游限流、连接池和 GPU 并发限制。

WebClient 调用不要再包一层虚拟线程；Reactor Netty 已经负责非阻塞网络 I/O。

## 10. 取消、超时和错误

每个下游请求都必须具备：

```text
连接超时
首响应超时
总响应超时
客户端取消
HTTP 错误映射
响应解析错误
```

流生命周期中使用：

```java
.timeout(timeout)
.doOnCancel(this::cancelDownstream)
.doFinally(signal -> releaseResources(signal))
```

错误事件必须包含稳定的 `errorCode`，但不能把下游 URL、Session、完整请求体返回给客户端。

第一阶段默认策略：客户端断开后取消任务和上游模型请求。如果产品要求断线后继续生成，再增加任务快照、查询接口、序列号和重连机制。

## 11. 测试要求

### 11.1 单元测试

使用 `StepVerifier` 测试：

- Token 拼接结果；
- 32 条触发刷新；
- 50ms 触发刷新；
- 状态事件不被文本吞掉；
- 错误和完成事件顺序；
- 取消信号向上游传播；
- RAG/DPU 并行调用；
- 所有异常都有终止事件。

### 11.2 集成测试

使用 MockWebServer 或 WireMock 模拟：

```text
首块延迟 1 秒
后续块每 100ms 返回
中途错误
客户端取消
慢客户端
```

验证客户端能在首块到达时收到 SSE，而不是等待下游完整结束。

### 11.3 代码扫描

执行以下检查：

```text
搜索 block(
搜索 .get(
搜索 .join(
搜索 await(
搜索 subscribe(
搜索 RestTemplate
搜索 SseEmitter
```

生产业务代码中除测试和明确的阻塞适配器外，不应出现这些调用。

## 12. 交付验收标准

- 启动时只使用 WebFlux，不加载 Spring MVC。
- HTTP 首个事件在下游首块到达后及时输出。
- 业务层返回 `Flux<ChatEvent>`，不依赖 HTTP 类型。
- LLM、RAG、DPU 的响应式 Client 可以单独测试。
- 客户端取消可以释放下游连接。
- 文本批量合并但不丢 Token；状态、错误、完成保持有序。
- 通过服务级压测和全链路压测分别得到容量数据。
- 虚拟线程只用于阻塞兼容层，不作为无限并发方案。
- 后续新增 A2A 时只增加 Adapter，不修改 Workflow 和核心事件模型。

