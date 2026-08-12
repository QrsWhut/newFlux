# 双模式金融对话与 ReAct Agent 改造 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不破坏现有固定工作流的前提下，为金融对话服务增加可控的 ReAct Agent 执行模式，让模型根据多轮上下文自主调用 RAG、DPU 等工具，并统一通过 SSE 输出。

**Architecture:** Controller 将请求交给执行模式路由器；`WORKFLOW` 继续执行当前固定 Stage 链路，`AGENT` 进入由模型工具调用驱动的 ReAct Loop。Agent 只在单次 HTTP 请求内循环；当信息不足时直接输出普通澄清文本并完成本轮，不维护 `WAITING_FOR_USER` 或长连接等待。会话记忆由服务端维护为“历史摘要 + 最近三轮完整问答”，工具轨迹只属于当前轮，不写入长期对话历史。

**Tech Stack:** Java 21、Spring Boot 3.3 WebFlux、Project Reactor、FastJSON、WebClient、JUnit 5、Reactor Test。

---

## 已确认的产品决策（实现时不得改变）

1. 存在 `WORKFLOW` 与 `AGENT` 两种模式；前者保持当前固定编排，后者由 LLM 自主选择工具与调用次数。
2. `AGENT` 模式不执行 `RewriteStage`。用户原话和会话上下文直接传给模型；RAG 查询词优化是工具内部行为，不能改写或替换用户原问题。
3. 模型认为信息不足时，直接输出普通文本反问并以 `COMPLETE` 结束本轮。**不**识别“反问”文本、不增加 `CLARIFICATION` SSE 事件、不创建跨请求的 `WAITING_FOR_USER` 状态。
4. 用户的补充回答是下一轮普通请求。因为上一轮澄清文本在近期对话历史中，模型自然可理解该补充。
5. 对话一轮是“一次用户问题 + 一次完整助手最终输出”；中间工具调用不计入轮次。近期保留三轮完整问答，较早轮次压缩为结构化摘要。
6. 长期记忆不保存原始 RAG/DPU 数据或内部推理；实时行情、新闻、研报等每轮按需重新查询。
7. 不向客户端发送模型原始思维链；只发送脱敏、面向用户的工具状态，如“正在查询行情”。

## 先决条件与接口决策

在编写 Agent 前，先向 LLM 网关确认兼容接口是否支持 OpenAI 风格的 `tools` / `tool_calls`（包含流式 tool-call delta 和工具结果 message）。当前 `LlmClient` 只处理文本和 reasoning 字段，无法运行真实的工具调用循环。

- 若网关支持：使用原生 function calling，作为唯一生产协议。
- 若网关不支持：先停止 Agent 功能开发，要求网关提供该能力；不得用“模型输出 JSON 字符串后正则解析”的方式在生产环境替代。该降级方案只可用于本地 mock 验证，且必须使用 FastJSON 严格校验。
- 工具调用的模型输出类型只用于 Loop 分支：`tool_call` 继续执行，纯文本 final response 直接结束。本方案没有“反问输出类型”。

### Task 1: 固化执行模式与请求边界

**Files:**
- Create: `main/java/com/example/chat/common/enums/ExecutionMode.java`
- Modify: `main/java/com/example/chat/web/vo/ChatRequestVO.java`
- Modify: `main/java/com/example/chat/common/dto/ChatRequest.java`
- Modify: `main/java/com/example/chat/web/controller/ChatController.java`
- Create: `test/java/com/example/chat/web/vo/ChatRequestVOTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ChatRequestVOTest test`

**Risk Level:** Standard lightweight verification.
**Why:** 仅扩展入口 DTO，但必须确保默认请求继续走当前工作流。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

为以下映射创建测试：未传模式时为 `WORKFLOW`；`"agent"` 映射为 `AGENT`；未知值返回参数校验错误；历史记录不能为 `null`。

**Step 3: Write minimal implementation**

- 新增枚举 `ExecutionMode { WORKFLOW, AGENT }`，枚举字段使用中文 Javadoc。
- `ChatRequest` 新增 `ExecutionMode executionMode`，不再通过 `attributes["mode"]` 承担模式语义。
- `ChatRequestVO` 新增 `executionMode` 字段，默认 `WORKFLOW`；保留现有 `mode` 字段并标记为 `@Deprecated`，仅为旧调用方映射 `1 -> WORKFLOW`、`2 -> AGENT`。
- 保持 `/api/chat/stream` URL、入参和 SSE 返回格式不变。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ChatRequestVOTest test`

Expected: PASS。

**Step 5: Record a local checkpoint**

记录执行模式已从弱类型 attributes 移至 `ChatRequest` 强类型字段，旧 mode 请求仍兼容。

### Task 2: 将现有工作流抽出为独立执行器，并增加路由层

**Files:**
- Create: `main/java/com/example/chat/service/interf/ChatExecutionService.java`
- Create: `main/java/com/example/chat/service/implement/ChatExecutionRouter.java`
- Modify: `main/java/com/example/chat/service/implement/ChatServiceImpl.java`
- Modify: `main/java/com/example/chat/web/controller/ChatController.java`
- Modify: `test/java/com/example/chat/ChatServiceTest.java`
- Create: `test/java/com/example/chat/service/implement/ChatExecutionRouterTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ChatExecutionRouterTest,ChatServiceTest test`

**Risk Level:** High-risk TDD.
**Why:** 这是双模式隔离的主路由，路由错误会改变所有线上请求的执行路径。

**Step 1: Set verification path**

先写 Router 测试：`WORKFLOW` 只调用工作流执行器；`AGENT` 只调用 Agent 执行器；执行器异常应透传为已有 SSE 错误事件。

**Step 2: Prepare verification**

使用两个 stub `ChatExecutionService` 和 `StepVerifier` 验证事件来源及订阅次数。

**Step 3: Write minimal implementation**

- `ChatExecutionService` 暴露 `ExecutionMode executionMode()` 与 `Flux<ChatEvent> stream(ChatRequest request)`。
- 将现有 `ChatServiceImpl` 更名或包裹为 `WorkflowChatExecutionService`，其内部 Stage 顺序完全不变，仍实现既有 `ChatService` 以减少改动面。
- `ChatExecutionRouter` 在构造期将执行器按 `ExecutionMode` 建立不可变 `Map`，拒绝重复注册与缺失模式。
- Controller 改为依赖 Router；Controller 不得包含 `if/else` 业务编排。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ChatExecutionRouterTest,ChatServiceTest test`

Expected: 先失败，再在实现后 PASS。

**Step 5: Record a local checkpoint**

记录默认 WORKFLOW 的所有既有 `ChatServiceTest` 仍通过。

### Task 3: 定义 Agent LLM 协议与网关适配器

**Files:**
- Create: `main/java/com/example/chat/agent/model/AgentMessage.java`
- Create: `main/java/com/example/chat/agent/model/AgentToolDefinition.java`
- Create: `main/java/com/example/chat/agent/model/AgentToolCall.java`
- Create: `main/java/com/example/chat/agent/model/AgentModelResponse.java`
- Create: `main/java/com/example/chat/agent/client/AgentLlmClient.java`
- Create: `main/java/com/example/chat/agent/client/WebClientAgentLlmClient.java`
- Modify: `main/java/com/example/chat/config/WebClientConfig.java`
- Modify: `main/resources/application.yml`
- Create: `test/java/com/example/chat/agent/client/WebClientAgentLlmClientTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=WebClientAgentLlmClientTest test`

**Risk Level:** High-risk TDD.
**Why:** Tool-call 协议错误会导致错误工具执行或无法结束 Loop。

**Step 1: Set verification path**

先以 MockWebServer 覆盖：普通流式最终文本、一个工具调用、多个并行工具调用、无效 JSON、网关 4xx/5xx、超时和客户端取消。

**Step 2: Prepare verification**

准备真实网关的脱敏样例报文，并在测试内断言 payload 包含：`messages`、`tools`、`tool_choice=auto`、`stream` 与会话认证头；不得在日志中记录 token 或完整用户会话。

**Step 3: Write minimal implementation**

- 不修改现有 `LlmClient`/`LlmRequest` 的工作流协议；Agent 使用独立 `AgentLlmClient`。
- `AgentModelResponse` 使用受限类型表达“工具调用”或“最终文本”，不能以字符串前缀判断。
- 网关流中有 tool call 时，缓冲完整、合法的参数 JSON 后才产生 `AgentToolCall`；有最终文本时才向上游输出文本 delta。
- 所有 JSON 编解码使用 FastJSON；所有新增注释和 Javadoc 使用正常中文 UTF-8（无 BOM）。
- 在 `application.yml` 新增 `agent.llm` 配置：模型、最大回合数、总超时、单模型调用超时。机密 token 使用环境变量或既有密钥机制，禁止提交到 YAML。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=WebClientAgentLlmClientTest test`

Expected: 先失败，再在实现后 PASS。

**Step 5: Record a local checkpoint**

记录所对接网关的 tool-call 报文版本与 mock 样例来源。

### Task 4: 建立受限工具注册表并复用 RAG/DPU 客户端

**Files:**
- Create: `main/java/com/example/chat/agent/tool/AgentTool.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolResult.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolRegistry.java`
- Create: `main/java/com/example/chat/agent/tool/RagAgentTool.java`
- Create: `main/java/com/example/chat/agent/tool/DpuAgentTool.java`
- Create: `main/java/com/example/chat/agent/tool/ResolveEntityAgentTool.java`
- Modify: `main/java/com/example/chat/integration/client/RagClient.java`
- Modify: `main/java/com/example/chat/integration/client/DpuClient.java`
- Create: `test/java/com/example/chat/agent/tool/AgentToolRegistryTest.java`
- Create: `test/java/com/example/chat/agent/tool/RagAgentToolTest.java`
- Create: `test/java/com/example/chat/agent/tool/DpuAgentToolTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentToolRegistryTest,RagAgentToolTest,DpuAgentToolTest test`

**Risk Level:** High-risk TDD.
**Why:** 工具输入来自模型，必须防止越权、无效参数和重复调用。

**Step 1: Set verification path**

先测参数校验、未知工具拒绝、单工具超时、可降级下游错误、重复参数调用去重，以及 RAG/DPU 结果转换为可供模型观察的受限文本。

**Step 2: Prepare verification**

使用 fake `RagClient`、`DpuClient`、`NerClient`，不连接真实网络。

**Step 3: Write minimal implementation**

- 第一版仅开放 `searchFinancialDocuments`（RAG）、`queryFinancialData`（DPU）、`resolveFinancialEntity`（基于现有 NER，返回候选实体）。不把 Rewrite、Followup、Viewpoint 暴露成 Agent 工具。
- 工具 schema 使用明确字段：`query`、`entity`、`metricNames`、`timeRange`。工具内部可以适配现有仅接收自然语言的下游接口。
- 结果统一为 `AgentToolResult(toolName, success, observation, uiNode)`；`observation` 需截断到配置的最大字符数，`uiNode` 可选，用于前端数据卡片。
- Agent Tool 不得调用 `subscribe()`、`block()` 或创建线程；全部返回 `Mono<AgentToolResult>`。
- Tool Registry 使用白名单，不支持模型指定任意 URL、SQL、类名或 prompt。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentToolRegistryTest,RagAgentToolTest,DpuAgentToolTest test`

Expected: 先失败，再在实现后 PASS。

**Step 5: Record a local checkpoint**

记录第一版工具白名单和每个工具的参数限制。

### Task 5: 实现三轮窗口与摘要式服务端会话记忆

**Files:**
- Create: `main/java/com/example/chat/agent/memory/ConversationTurn.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationMemory.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationMemoryRepository.java`
- Create: `main/java/com/example/chat/agent/memory/InMemoryConversationMemoryRepository.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationMemoryService.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationSummaryService.java`
- Create: `test/java/com/example/chat/agent/memory/ConversationMemoryServiceTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ConversationMemoryServiceTest test`

**Risk Level:** High-risk TDD.
**Why:** 多轮上下文错误会直接造成错标的、错时间范围或用户数据串会话。

**Step 1: Set verification path**

先写测试覆盖：前三轮完整保留；第 4 轮提交时第 1 轮被压缩到摘要；工具数据不进入持久记忆；澄清文本与用户补充作为普通相邻轮；不同 `userId + sessionId` 绝不共享记忆。

**Step 2: Prepare verification**

为摘要服务使用 stub，验证其入参含“原摘要 + 被淘汰轮次”，不含原始工具 observation。

**Step 3: Write minimal implementation**

- 会话 key 使用 `userId + sessionId`；请求缺失任一字段必须拒绝。
- `ConversationMemory` 包含 `summary`、最多三条 `ConversationTurn`、版本号；工具 trace 只保留在当前 `AgentTurnContext`。
- 摘要采用结构化中文字段：已确认实体、用户目标/偏好、重要约束、已得结论、未解决问题。不得把历史行情数值写成“当前事实”。
- `InMemoryConversationMemoryRepository` 仅用于开发和测试，必须在类 Javadoc 标明生产环境需替换为 Redis/数据库实现；通过原子更新避免同会话写丢失。
- 生产接入存储的选型和评审不在本次编码范围；接口必须让后续替换无需改 Agent。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=ConversationMemoryServiceTest test`

Expected: 先失败，再在实现后 PASS。

**Step 5: Record a local checkpoint**

记录内存存储仅用于非生产环境的限制。

### Task 6: 编写 Agent 系统提示词与上下文组装器

**Files:**
- Create: `main/java/com/example/chat/agent/prompt/AgentPromptFactory.java`
- Create: `main/java/com/example/chat/agent/AgentTurnContext.java`
- Create: `test/java/com/example/chat/agent/prompt/AgentPromptFactoryTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentPromptFactoryTest test`

**Risk Level:** Standard lightweight verification.
**Why:** 主要是上下文顺序和产品策略固化，不包含并发循环。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

测试最终 message 顺序固定为：系统策略、页面上下文、历史摘要、最近三轮、当前问题、当前轮工具 observation；断言不包含内部 reasoning 和旧工具原始数据。

**Step 3: Write minimal implementation**

系统提示词必须明确：

```text
你是金融对话助手。基于会话上下文理解指代，不执行固定问句改写。
对实时行情、财务指标使用 queryFinancialData；对新闻和研报使用 searchFinancialDocuments；
对象无法确定时优先 resolveFinancialEntity。若关键信息仍不足，直接简洁地询问用户，
不要猜测、不要调用无意义工具；该澄清文本就是本轮最终回答。
不得编造工具结果；工具失败时说明限制并给出下一步建议。
```

页面数据大小须设上限并标注来源；工具 result 作为 `tool` message 加回本轮上下文，而非写入会话记忆。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentPromptFactoryTest test`

Expected: PASS。

**Step 5: Record a local checkpoint**

记录 Prompt 版本号，并将该版本写入 Agent 运行审计日志。

### Task 7: 实现无阻塞 ReAct Loop 与 Agent SSE 映射

**Files:**
- Create: `main/java/com/example/chat/service/implement/AgentChatExecutionService.java`
- Create: `main/java/com/example/chat/agent/AgentLoop.java`
- Modify: `main/java/com/example/chat/common/dto/ChatEvent.java`
- Modify: `main/java/com/example/chat/common/enums/ChatEventType.java`
- Modify: `main/java/com/example/chat/task/TaskCancellationService.java`
- Create: `test/java/com/example/chat/agent/AgentLoopTest.java`
- Create: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentLoopTest,AgentChatExecutionServiceTest test`

**Risk Level:** High-risk TDD.
**Why:** 这是本次改造的核心递归/取消路径，最容易发生循环、重复调用或流未结束。

**Step 1: Set verification path**

先写 `StepVerifier` 用例：无工具直接输出；RAG 后输出；RAG+DPU 并行后输出；未知工具；工具可降级失败；模型超过最大步数；客户端取消；模型输出澄清文本并正常 COMPLETE。

**Step 2: Prepare verification**

用 fake `AgentLlmClient` 依次返回：工具调用、观察后的最终文本。断言澄清文本和普通回答走同一 `TEXT_DELTA + COMPLETE` 路径。

**Step 3: Write minimal implementation**

- `AgentLoop` 使用 `Mono.defer` / `flatMapMany` 递归或等价 Reactor 编排；严禁 `while` 内 `block()`、手工 `subscribe()`、显式建线程。
- 每次模型返回 `tool_call`：先发 `STATUS`，执行白名单工具，必要时发 `UI_UPDATE`，将 observation 追加到当前回合，再进入下一次模型调用。
- 多个彼此独立的工具调用可 `Mono.zip` 并行执行；事件对前端仍按稳定顺序发出。
- 模型返回最终文本：流式转换为 `TEXT_DELTA`，文本结束后只发一个 `COMPLETE`，并触发 Task 8 的记忆落库。
- Agent mode 可新增 `TOOL_CALL`、`TOOL_RESULT` 类型给调试面板，但前端不得展示参数中的敏感信息；如果前端未改造，则仅使用现有 `STATUS/UI_UPDATE`，不强制协议升级。
- 设置硬边界：最大 4 个模型决策回合、同参数同工具本轮只允许一次、每工具 10 秒、单任务总 60 秒。到达边界生成可读 `ERROR` 事件。
- 用户取消必须取消当前模型流及正在执行的 WebClient 工具请求；不得写入不完整 assistant turn。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentLoopTest,AgentChatExecutionServiceTest test`

Expected: 先失败，再在实现后 PASS。

**Step 5: Record a local checkpoint**

记录最大步数、超时、去重策略和取消行为的测试结果。

### Task 8: 在成功完成后写入记忆，并保持工作流行为不变

**Files:**
- Modify: `main/java/com/example/chat/service/implement/AgentChatExecutionService.java`
- Modify: `main/java/com/example/chat/service/implement/ChatServiceImpl.java`
- Modify: `test/java/com/example/chat/ChatServiceTest.java`
- Modify: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`

**Risk Level:** High-risk TDD.
**Why:** 流式完成、取消、异常和记忆提交顺序必须严格一致。

**Step 1: Set verification path**

先补 Agent 成功、反问成功、工具失败后正常回答、错误、取消五类用例；只验证正常 `COMPLETE` 后才写入一条完整 turn。

**Step 2: Prepare verification**

复用内存仓库，断言取消和 ERROR 后对话记忆未新增 assistant 最终回答。

**Step 3: Write minimal implementation**

- Agent 将最终聚合的文本作为 assistant answer 写入 `ConversationMemoryService`，再完成 response；若内存写入失败，记录具体错误日志但不把已生成答案重复推送。
- Workflow 模式不读取或写入 Agent 服务端记忆，避免改变当前客户端 `history` 驱动方式；后续另行评估统一记忆。
- 删除 Agent 链路对 `RewriteStage`、`RewriteClient`、`DatasetClient` 的依赖；这些类暂不删除，仍属于 Workflow。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`

Expected: PASS，且既有 `ChatServiceTest` 全部通过。

**Step 5: Record a local checkpoint**

记录 WORKFLOW 与 AGENT 的记忆边界及回归结果。

### Task 9: 更新 mock、文档与可观测性

**Files:**
- Modify: `main/java/com/example/chat/web/controller/MockDownstreamController.java`
- Modify: `main/resources/application.yml`
- Create: `docs/agent-mode-api.md`
- Modify: `README.md`（若存在）
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`

**Risk Level:** Standard lightweight verification.
**Why:** 保证本地可重复演示和交接，不改变核心路径。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

增加三个 mock 场景：无工具澄清、RAG 后回答、DPU + RAG 后回答；使用 curl 验证 `executionMode=AGENT` 返回合法 SSE。

**Step 3: Write minimal implementation**

- 文档给出请求样例、SSE 示例、三种模式场景和网关 tool-call 报文契约。
- 日志只记录 taskId、脱敏 sessionId、模式、工具名、耗时、结果大小、错误码和 Prompt 版本；禁止记录会话原文、认证信息、完整工具结果和 reasoning。
- 修复本次触及文件中已有乱码中文注释，确保 UTF-8 无 BOM。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`

Expected: PASS。

**Step 5: Record a local checkpoint**

记录本地 mock 演示命令和 Agent 生产前仍需替换的会话存储实现。

## 验收场景

1. 不传模式的旧请求仍以原有固定顺序运行，全部既有测试通过。
2. `AGENT` 请求“贵州茅台最近怎么样”可由模型调用 RAG、DPU 中任意一个或多个，不会执行 RewriteStage。
3. 连续对话“贵州茅台最近怎么样”→“那它的市盈率呢”，第二轮上下文能确定“它”指代贵州茅台。
4. “帮我看新能源龙头”在模型认为必要时输出普通澄清问题并 COMPLETE；用户下一轮输入“宁德时代”后可继续查询，不存在遗留等待任务。
5. 超过三轮时，最早完整问答进入结构化摘要；实时数据与原始工具输出不进入摘要。
6. Agent 循环超过限制、未知工具、下游超时和用户取消均不产生重复 COMPLETE、不泄露密钥、不保存半轮对话。

## 非目标

- 本次不删除 Workflow 的 Rewrite/RAG/DPU/Followup Stage。
- 本次不实现多 Agent、计划树、跨请求 Agent 恢复或长时间阻塞等待。
- 本次不将模型思维链暴露给前端。
- 本次不将内存会话仓库作为生产存储方案。
