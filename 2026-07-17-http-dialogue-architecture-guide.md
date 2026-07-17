# HTTP 对话流式服务架构讲解文档

## 1. 为什么要拆成这些层

这次重构不是简单把 `RestTemplate` 换成 `WebClient`，而是把“业务流程”和“网络传输”分开。

```text
Controller：如何接收和返回 HTTP
Service：这次对话要做什么
Workflow Stage：每个业务阶段怎么执行
Client：如何调用某一个下游
ChatEvent：过程中产生了什么结果
```

这样做以后，HTTP、A2A 或其他协议都可以消费同一个 `Flux<ChatEvent>`，不会把协议细节散落在业务代码中。

## 2. Flux 在项目中的作用

`Flux` 可以理解为“未来会陆续产生的一组结果”。大模型 Token、状态变化、RAG 结果都可以放进同一条事件流。

```text
下游数据到达
  -> Flux 发出事件
  -> Workflow 继续编排
  -> Controller 转成 SSE
  -> 客户端逐个接收
```

`Flux` 本身不是线程池，也不是协议。它主要描述数据如何产生、转换、合并、取消和结束。

## 3. WebFlux 和虚拟线程如何配合

两者解决的问题不同：

```text
WebFlux + Reactor Netty：网络等待时不占一个专属平台线程
虚拟线程：兼容暂时不能改造的同步代码
```

如果所有下游都已经是 WebClient，主流程不需要再使用虚拟线程。虚拟线程保留给旧 SDK、同步数据库和其他阻塞依赖，避免它们阻塞 Reactor EventLoop。

## 4. 为什么业务层不能返回 SseEmitter

`SseEmitter` 是 Spring MVC 的 HTTP 输出对象，只适合 Controller 边界。业务层如果返回它，就会产生以下问题：

- Workflow 只能服务 HTTP，无法复用。
- 测试必须模拟 HTTP 响应。
- A2A 或消息队列无法复用业务结果。
- 业务代码会直接关心 send、complete 和客户端连接。

新的做法是：

```text
业务层：Flux<ChatEvent>
HTTP层：Flux<ServerSentEvent<String>>
未来A2A层：Flux<对应协议事件>
```

## 5. 为什么需要事件合并

大模型可能非常快地返回许多小 Token。如果每个 Token 都执行一次：

```text
对象创建 -> JSON 序列化 -> SSE 写出 -> Nginx 转发 -> 前端渲染
```

网络包、序列化和浏览器刷新次数都会增加。`bufferTimeout(32, 50ms)` 的含义是：

```text
最多收集 32 个文本块
或者最多等待 50ms
先满足哪个条件就发送
```

它不是为了改变答案，而是把多个连续小文本块合并成一个更大的增量。

状态切换、错误、完成和取消是控制事件，不能与普通文本无限等待或乱序。

## 6. 背压应该怎样理解

WebFlux 的响应式链路可以感知下游写出速度；如果客户端很慢，数据流会受到反向影响。但普通 HTTP SSE 客户端不会像 Reactive Streams 一样显式发送 `request(n)`，所以不能说“HTTP 协议天然提供完整背压”。

本项目采用三层保护：

1. `bufferTimeout` 限制小事件数量和等待时间。
2. WebFlux 负责响应式写出和取消传播。
3. WebClient、连接池和业务信号量限制下游并发。

如果压测发现慢客户端导致内存持续增长，再增加有界待发送策略；不能在还没有证据时随意丢弃原始 Token。

## 7. 一个请求的完整生命周期

```text
1. Controller 接收请求并转换成 ChatRequest
2. ChatService 创建 ChatContext
3. RewriteStage 调用问句改写
4. FirstAnswerStage 读取 LLM Flux
5. ChatEventAssembler 合并文本并发出事件
6. Workflow 判断是否需要 RAG / DPU
7. EnrichmentStage 使用 Mono.zip 并行请求
8. SecondAnswerStage 继续读取 LLM Flux
9. FollowupStage 生成追问
10. Controller 将 ChatEvent 转为 SSE
11. 客户端断开时取消 Flux，并取消当前下游请求
12. 完成或失败时发送终止事件
```

## 8. 为什么不能在响应式代码中 block

如果在 WebClient 的回调或 Reactor EventLoop 中执行 `block()`、同步 HTTP 或 `Thread.sleep()`，一个线程可能同时影响它管理的多个连接，最终出现延迟扩大。

因此，遇到阻塞依赖时只有两种选择：

```text
优先：替换为响应式 Client
暂时无法替换：Mono.fromCallable(...).subscribeOn(阻塞调度器)
```

不要用“把所有代码放进线程池”作为默认方案，因为那只是把阻塞转移到另一批线程。

## 9. 为什么先做 HTTP，再接 A2A

HTTP + SSE 可以先验证最重要的内容：

- 事件模型是否合理；
- UINode 更新是否正确；
- 文本合并是否影响前端体验；
- 取消和超时是否可靠；
- 本服务和下游的并发上限是多少。

这些能力属于业务核心，不应该被 A2A SDK 的返回类型和生命周期限制。等 HTTP 版本稳定后，再增加：

```text
ChatEvent -> A2A Event Adapter -> A2A SDK
```

这样 A2A 只是协议出口，不会重新污染 Workflow。

## 10. 交给其他 AI 实施时的执行要求

必须要求执行 AI：

1. 先创建项目骨架和核心模型，再逐步接入下游。
2. 每完成一个模块先运行测试，不要一次生成整个项目后再调试。
3. 所有新增类写中文 Javadoc，说明职责、输入、输出、异常和取消行为。
4. 先使用 Mock 下游验证流式、超时、错误和取消，再替换真实地址。
5. 每次改动后检查是否引入 MVC、RestTemplate、SseEmitter 或阻塞等待。
6. 不为了“看起来响应式”而把同步代码简单包装成 `Mono.just`；同步代码必须用 `fromCallable` 并切换到阻塞调度器。
7. 不把线程数、连接数直接写成 QPS，容量必须通过压测获得。

