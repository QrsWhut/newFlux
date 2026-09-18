# Production Agent Platform Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将现有 Demo 改造成可运行、可审计、可扩展的服务端 ReAct Agent 系统，采用自主管理上下文、OpenAI Responses 协议、多供应商模型路由、Harness 治理、MySQL Turn 记忆和可灰度的 Wind MCP 金融能力。

**Architecture:** 以 `AgentHarness` 作为唯一执行入口，依次完成身份与幂等校验、会话租约、版本化 Prompt 与上下文编译、Token 预算、模型路由、Responses 流解析、工具权限/执行/审计、Turn 提交和结构化完成事件。模型层使用供应商无关的内部 Responses 协议，由路由选择 provider、model 和 API Key；金融工具继续保留现有模型可见门面，通过 Gateway/Adapter 在 Legacy 与 Wind MCP 之间受控切换。

**Tech Stack:** Java 21、Spring Boot 3.3、Spring WebFlux、Reactor Netty、FastJSON、MyBatis、MySQL、Lombok、Micrometer、JUnit 5、Reactor Test、MockWebServer

---

## 1. 需求结论

1. 保留现有 `AgentToolRegistry`、`AgentToolInvoker`、严格 JSON Schema、权限、审计、并行执行与取消传播。
2. 停止运行旧 Prompt ID 与旧网关模型调用，移除硬编码凭据和包含内部数据的调试输出。
3. 以 OpenAI Responses 的 input item、function call、function call output、typed SSE 和 usage 作为内部协议；不把 Chat Completions 当作新主链路。
4. Prompt 由本项目中的版本化 Markdown 管理，运行时生成 content hash；数据库只记录版本和 hash，不按远端 ID 获取模板。
5. 模型路由按调用用途选择 provider、model、endpoint、reasoning、上下文窗口和输出上限；API Key 只允许从环境或 Secret Provider 注入。
6. Harness 是所有 ReAct 调用不可绕过的治理层，统一处理 deadline、步骤、预算、租约 fencing、工具策略、审计、持久化与错误分类。
7. 严格实施 `2026-08-25-agent-conversation-memory-persistence-and-compaction.md`，并补齐 fencing epoch、模型 attempt、provider/route/hash 审计和摘要提示注入防护。
8. MySQL 中以完整 Turn 为事实源，工具调用、摘要和每次模型调用分别记录；不保存隐藏推理和完整工具大结果。
9. Wind MCP 首期作为现有 RAG/DPU 门面后的 Provider 接入，不动态向模型暴露全部远程工具，不引入 Node 子进程。
10. 同时保留 `/api/chat/stream`，新增 OpenAI Responses 风格的服务入口；旧客户端的 WORKFLOW 值兼容路由到新 Agent，不再触发旧模型链路。

## 2. 已采用默认决策

- Responses 请求使用 `store=false`，会话由本服务管理；手工回放时保留可观察协议 item，不保存隐藏 chain-of-thought。
- 主路由和压缩路由独立配置；默认示例分别使用质量路由和低成本路由，真实模型可用环境变量覆盖。
- Prompt 资源位于 `main/resources/prompts`，内容、版本和 SHA-256 hash 构成不可变快照。
- 摘要作为低权限、不可信历史数据块回放，不提升为可覆盖系统策略的 developer 指令。
- MySQL 为生产存储；显式 local/test 模式可使用内存 Store，不能在生产配置中静默回退。
- usage 锚点首版默认关闭，只采集估算误差；影子统计稳定后再启用。
- Wind 首期不接入耗时的 Alice 外部 Agent；模型只看到现有 RAG/DPU/实体解析稳定门面。
- Wind Provider 模式支持 `LEGACY_ONLY`、`WIND_ONLY`、`WIND_PREFERRED`、`SHADOW`，默认 `LEGACY_ONLY`。
- Wind 鉴权、配额、限流错误不能静默切 Legacy；默认不持久化 Wind 原始大结果。
- 失败/取消 Turn 以明确内部状态记录参与摘要，不伪造 assistant 回答。
- `latestSummaryEndTurnNo` 通过最新摘要指针读取，不在会话表重复保存。
- 模型网络重试只允许发生在尚未收到任何响应事件且错误明确可重试时，所有 attempt 单独计费和审计。

## 3. 当前仍需外部确认

- Wind skill 安装范围必须由用户明确选择“当前项目”或“全局”；未确认前禁止执行安装命令。
- 生产 provider 的 base URL、允许模型清单和 Secret 名称；禁止在对话或提交中传真实 Key。
- MySQL 地址、账号、建表发布工具和备份策略。
- 可信用户身份来源，以及 session/task 的授权和取消权限模型。
- Wind 合同是否允许缓存、生成摘要、长期保存和向终端用户再分发，以及保留期限。
- 前端对 `RESET_RECOMMENDED` 和 OpenAI Responses SSE 的最终展示约定。

上述事项不阻塞本地实现；代码通过安全默认值、显式配置和 fail-closed 行为保留边界。

## 4. Harness 主链路

```text
HTTP Admission
→ Identity / Authorization
→ taskId Idempotency
→ Conversation Lease + Fencing Epoch
→ PROCESSING Turn
→ Prompt Snapshot + Route + Allowed Tools Freeze
→ Context Compile + Token Budget
→ Model Call Ledger + Responses Gateway
→ Tool Policy + Validation + PENDING Record
→ Tool Execution + Sanitized Observation + Result Summary
→ Context Recompile + Budget
→ Final Answer Commit
→ COMPLETE / RESET_RECOMMENDED
→ Reliable Lease Release
```

## 5. 实施任务

### Task 1: 固化配置、状态码和 Prompt 快照

**Files:**
- Create: `main/java/com/example/chat/config/AiRoutingProperties.java`
- Create: `main/java/com/example/chat/config/AgentMemoryProperties.java`
- Create: `main/java/com/example/chat/config/WindProperties.java`
- Modify: `main/java/com/example/chat/config/AgentProperties.java`
- Create: `main/java/com/example/chat/common/enums/ModelCallType.java`
- Create: `main/java/com/example/chat/common/enums/ModelCallStatus.java`
- Create: `main/java/com/example/chat/common/enums/CompressionLevel.java`
- Create: `main/java/com/example/chat/common/enums/ContextZone.java`
- Create: `main/java/com/example/chat/common/enums/ContextErrorCode.java`
- Create: `main/java/com/example/chat/agent/prompt/PromptPurpose.java`
- Create: `main/java/com/example/chat/agent/prompt/PromptSnapshot.java`
- Create: `main/java/com/example/chat/agent/prompt/PromptCatalog.java`
- Create: `main/java/com/example/chat/agent/prompt/ClasspathPromptCatalog.java`
- Create: `main/resources/prompts/financial-agent-system.md`
- Create: `main/resources/prompts/conversation-summary.md`
- Modify: `main/resources/application.yml`
- Verify: `test/java/com/example/chat/config/AiRoutingPropertiesTest.java`
- Verify: `test/java/com/example/chat/agent/prompt/ClasspathPromptCatalogTest.java`

**Risk Level:** Standard lightweight verification
**Why:** 配置和值对象本身简单，但其默认值和 hash 是后续审计与预算的输入。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

验证 provider/route 默认值、非法 ratio 拒绝、Secret 缺失时不在启动日志泄露、Prompt UTF-8 内容和 SHA-256 稳定性。

**Step 3: Write minimal implementation**

状态枚举使用稳定 code；所有阈值来自配置；Prompt Catalog 在启动时加载两个 Markdown 并生成不可变快照。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=AiRoutingPropertiesTest,ClasspathPromptCatalogTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录配置键、默认值、Prompt 版本/hash 和验证命令结果。

### Task 2: 建立供应商无关的 OpenAI Responses 模型协议

**Files:**
- Create: `main/java/com/example/chat/agent/model/AgentModelRequest.java`
- Create: `main/java/com/example/chat/agent/model/AgentModelUsage.java`
- Create: `main/java/com/example/chat/agent/model/AgentModelEvent.java`
- Create: `main/java/com/example/chat/agent/model/AgentInputItem.java`
- Modify: `main/java/com/example/chat/agent/model/AgentToolDefinition.java`
- Modify: `main/java/com/example/chat/agent/model/AgentToolCall.java`
- Modify: `main/java/com/example/chat/agent/client/AgentLlmClient.java`
- Create: `main/java/com/example/chat/agent/client/AiModelRouter.java`
- Create: `main/java/com/example/chat/agent/client/ConfiguredAiModelRouter.java`
- Create: `main/java/com/example/chat/agent/client/OpenAiResponsesClient.java`
- Replace: `main/java/com/example/chat/agent/client/WebClientAgentLlmClient.java`
- Modify: `main/java/com/example/chat/config/WebClientConfig.java`
- Verify: `test/java/com/example/chat/agent/client/OpenAiResponsesClientTest.java`
- Verify: `test/java/com/example/chat/agent/client/ConfiguredAiModelRouterTest.java`

**Risk Level:** High-risk TDD
**Why:** Typed SSE、function call 配对、usage 和错误映射决定整个 Agent 协议正确性和成本审计。

**Step 1: Set verification path**

先写 MockWebServer 用例，覆盖文本 delta、碎片化 function arguments、并行 calls、completed usage、空 usage、错误事件、HTTP 鉴权/限流/上下文超限和取消。

**Step 2: Prepare verification**

先确认旧客户端无法解析 `response.output_text.delta` 和 `response.output_item.done`，测试预期失败。

**Step 3: Write minimal implementation**

请求发送到路由确定的 `/v1/responses`，使用 Bearer Key、`store=false`、`max_output_tokens`、reasoning 和冻结工具定义；按 `call_id` 聚合 function call，解析 `response.completed.response.usage`。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=OpenAiResponsesClientTest,ConfiguredAiModelRouterTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录实际请求 JSON、支持的 SSE 事件、usage 映射和错误分类。

### Task 3: 移除 Prompt ID、硬编码凭据和旧模型运行链路

**Files:**
- Delete: `main/java/com/example/chat/integration/client/LlmClient.java`
- Delete: `main/java/com/example/chat/integration/client/WebClientLlmClient.java`
- Delete: `main/java/com/example/chat/common/dto/downstream/LlmRequest.java`
- Delete: `main/java/com/example/chat/common/dto/downstream/LlmChunk.java`
- Retire: `main/java/com/example/chat/service/implement/ChatServiceImpl.java`
- Retire: `main/java/com/example/chat/service/implement/workflow/FirstAnswerStage.java`
- Retire: `main/java/com/example/chat/service/implement/workflow/SecondAnswerStage.java`
- Retire: `main/java/com/example/chat/service/implement/workflow/EnrichmentStage.java`
- Retire: `main/java/com/example/chat/service/implement/workflow/RewriteStage.java`
- Retire: `main/java/com/example/chat/service/implement/workflow/FollowupStage.java`
- Modify: `main/java/com/example/chat/service/implement/ChatExecutionRouter.java`
- Modify: `main/java/com/example/chat/common/enums/ExecutionMode.java`
- Modify: `main/java/com/example/chat/web/vo/ChatRequestVO.java`
- Modify: `main/java/com/example/chat/integration/client/WebClientRewriteClient.java`
- Modify: `main/java/com/example/chat/web/controller/MockDownstreamController.java`
- Replace: `test/java/com/example/chat/ChatServiceTest.java`
- Delete: `test/java/com/example/chat/integration/client/WebClientLlmClientTest.java`
- Verify: secret and Prompt ID search

**Risk Level:** High-risk TDD
**Why:** 该任务删除生产中的旧执行分支并改变默认模式，必须保留旧请求字段的兼容行为。

**Step 1: Set verification path**

新增兼容测试：未传 mode、`mode=1`、`WORKFLOW` 和 `AGENT` 均进入新 Harness；不得注册旧 LLM Client。

**Step 2: Prepare verification**

运行 secret/Prompt ID 搜索，确认修改前存在命中。

**Step 3: Write minimal implementation**

移除旧模型类和工作流 Bean；保留 RAG/DPU/NER 等客户端与 Agent 工具；Rewrite 客户端若仍保留，凭据只从 Secret 配置注入且禁止 payload 日志。

**Step 4: Run chosen verification**

Run: `rg -n "P053102|P052184|P052186|promptID|pkey|PKey|Bearer [A-Za-z0-9+/=]{16,}|DEBUG_DUMP" main test`
Expected: no matches containing credentials or Prompt IDs

**Step 5: Record a local checkpoint**

记录已删除旧链路、仍保留的工具/下游客户端和兼容路由行为。

### Task 4: 实现上下文编译、Token 估算和预算守卫

**Files:**
- Create: `main/java/com/example/chat/agent/context/PreparedAgentContext.java`
- Create: `main/java/com/example/chat/agent/context/ContextTokenBreakdown.java`
- Create: `main/java/com/example/chat/agent/context/ContextBudgetDecision.java`
- Create: `main/java/com/example/chat/agent/context/ContextTokenEstimator.java`
- Create: `main/java/com/example/chat/agent/context/ContextBudgetService.java`
- Create: `main/java/com/example/chat/agent/context/AgentContextCompiler.java`
- Modify: `main/java/com/example/chat/agent/prompt/AgentPromptFactory.java`
- Verify: `test/java/com/example/chat/agent/context/ContextTokenEstimatorTest.java`
- Verify: `test/java/com/example/chat/agent/context/ContextBudgetServiceTest.java`
- Verify: `test/java/com/example/chat/agent/context/AgentContextCompilerTest.java`

**Risk Level:** High-risk TDD
**Why:** 安全阈值、硬限制、静态上下文和当前工具观察的分类是压缩与拒绝策略的核心边界。

**Step 1: Set verification path**

先覆盖中文、英文、代码、Prompt、实际工具 Schema、摘要、pageData、当前 Turn 和协议开销；断言恰好 safe、恰好 hard 和超过 hard。

**Step 2: Prepare verification**

证明预算和发送使用同一冻结工具列表；摘要以不可信数据块回放，不能成为 developer 指令。

**Step 3: Write minimal implementation**

实现按 UTF-8 字节和模型系数估算、分段明细、fingerprint 和具体错误分类；每次调用只接受不可变 `PreparedAgentContext`。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=ContextTokenEstimatorTest,ContextBudgetServiceTest,AgentContextCompilerTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录公式、阈值、误差配置、摘要安全边界与 fingerprint 输入。

### Task 5: 建立 MySQL DDL、MyBatis DAL 与 fencing 字段

**Files:**
- Modify: `../pom.xml`
- Create: `main/resources/db/schema/agent_conversation_memory.sql`
- Create: `main/java/com/example/chat/dal/model/AgentConversationDO.java`
- Create: `main/java/com/example/chat/dal/model/ConversationTurnDO.java`
- Create: `main/java/com/example/chat/dal/model/TurnToolCallDO.java`
- Create: `main/java/com/example/chat/dal/model/ConversationSummaryDO.java`
- Create: `main/java/com/example/chat/dal/model/ModelCallRecordDO.java`
- Create: `main/java/com/example/chat/dal/dao/AgentConversationMapper.java`
- Create: `main/java/com/example/chat/dal/dao/ConversationTurnMapper.java`
- Create: `main/java/com/example/chat/dal/dao/TurnToolCallMapper.java`
- Create: `main/java/com/example/chat/dal/dao/ConversationSummaryMapper.java`
- Create: `main/java/com/example/chat/dal/dao/ModelCallRecordMapper.java`
- Create: `main/resources/mapper/AgentConversationMapper.xml`
- Create: `main/resources/mapper/ConversationTurnMapper.xml`
- Create: `main/resources/mapper/TurnToolCallMapper.xml`
- Create: `main/resources/mapper/ConversationSummaryMapper.xml`
- Create: `main/resources/mapper/ModelCallRecordMapper.xml`
- Create: `main/java/com/example/chat/config/MyBatisMemoryConfiguration.java`
- Verify: `test/java/com/example/chat/dal/dao/ConversationMapperContractTest.java`

**Risk Level:** High-risk TDD
**Why:** 唯一键、状态条件更新、租约 fencing 和摘要 CAS 是持久一致性的基础。

**Step 1: Set verification path**

准备 Mapper 合约测试和 DDL 静态规则测试，覆盖单数表名、三字段、无外键、显式状态 code、租约恢复索引、attempt 唯一键。

**Step 2: Prepare verification**

先运行依赖/Mapper 测试看到预期失败；真实 MySQL 集成测试在用户提供测试库后补跑。

**Step 3: Write minimal implementation**

增加 MyBatis、Spring JDBC、Hikari 和 MySQL Driver；手动条件化 MySQL 配置，避免 local profile 无数据库时启动失败。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=ConversationMapperContractTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录 DDL、索引、条件更新、事务入口和尚未执行的真实 MySQL 验证。

### Task 6: 实现 Turn 事实层、幂等和会话执行租约

**Files:**
- Create: `main/java/com/example/chat/common/dto/agent/memory/ConversationSnapshotDTO.java`
- Create: `main/java/com/example/chat/common/dto/agent/memory/ConversationTurnDTO.java`
- Create: `main/java/com/example/chat/common/dto/agent/memory/ConversationToolCallDTO.java`
- Create: `main/java/com/example/chat/common/dto/agent/memory/ConversationSummaryDTO.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationStore.java`
- Create: `main/java/com/example/chat/agent/memory/MySqlConversationStore.java`
- Create: `main/java/com/example/chat/agent/memory/LocalConversationStore.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationMemoryManager.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationExecutionLease.java`
- Replace: `main/java/com/example/chat/agent/memory/ConversationMemoryService.java`
- Retire: `main/java/com/example/chat/agent/memory/ConversationMemoryRepository.java`
- Retire: `main/java/com/example/chat/agent/memory/InMemoryConversationMemoryRepository.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationMemoryManagerTest.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationExecutionLeaseTest.java`

**Risk Level:** High-risk TDD
**Why:** 该层负责事实源、重复 taskId 行为、终态不可变和旧 owner 写入隔离。

**Step 1: Set verification path**

覆盖 PROCESSING/SUCCESS/FAILED/CANCELLED、重复 taskId 三种终态、同 session 串行、租约过期后 fencing、最终持久化失败不发 COMPLETE。

**Step 2: Prepare verification**

先用 Local Store 验证状态不变量，再验证 MySQL Store 调用合约；所有阻塞调用必须在 boundedElastic。

**Step 3: Write minimal implementation**

同步短事务 Bean 完成多表写，外层 Manager 使用 `Mono.fromCallable(...).subscribeOn(boundedElastic())`；释放租约使用可等待的清理链路。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=ConversationMemoryManagerTest,ConversationExecutionLeaseTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录状态迁移、fencing 条件、幂等回放和响应式隔离验证。

### Task 7: 实现确定性 Turn 回放和两级摘要压缩

**Files:**
- Create: `main/java/com/example/chat/agent/memory/ConversationTurnReplayService.java`
- Replace: `main/java/com/example/chat/agent/memory/ConversationSummaryService.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationSummaryPublisher.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationContextCoordinator.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationTurnReplayServiceTest.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationSummaryServiceTest.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationContextCoordinatorTest.java`

**Risk Level:** High-risk TDD
**Why:** 工具 callId 配对、摘要覆盖范围和两级压缩错误会造成长期且隐蔽的上下文丢失。

**Step 1: Set verification path**

覆盖无工具、多步骤/并行工具、失败 Turn、结果摘要回放、普通压缩保留最近三轮成功 Turn、分批压缩、深度 SAFE/RISK/REJECT 和禁止第三次压缩。

**Step 2: Prepare verification**

使用固定摘要结构样本，验证空、超长、缺字段、冲突丢失均不能发布；两个发布者只能有一个 CAS 成功。

**Step 3: Write minimal implementation**

压缩模型调用和 usage 独立落账；摘要发布短事务 CAS；发布后重载并重新编译预算；摘要内容作为不可信历史数据。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=ConversationTurnReplayServiceTest,ConversationSummaryServiceTest,ConversationContextCoordinatorTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录回放协议、摘要格式、覆盖不变量、CAS 和压缩边界。

### Task 8: 将 ReAct 升级为 Harness 执行器

**Files:**
- Create: `main/java/com/example/chat/agent/harness/AgentHarness.java`
- Create: `main/java/com/example/chat/agent/harness/AgentHarnessPolicy.java`
- Create: `main/java/com/example/chat/agent/harness/AgentExecutionState.java`
- Create: `main/java/com/example/chat/agent/harness/AgentStepResult.java`
- Modify: `main/java/com/example/chat/agent/AgentLoop.java`
- Modify: `main/java/com/example/chat/agent/AgentTurnContext.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolResult.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolInvoker.java`
- Replace: `main/java/com/example/chat/service/implement/AgentChatExecutionService.java`
- Verify: `test/java/com/example/chat/agent/harness/AgentHarnessTest.java`
- Verify: `test/java/com/example/chat/agent/AgentLoopTest.java`
- Verify: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`

**Risk Level:** High-risk TDD
**Why:** 这是模型、工具、上下文、记忆、超时与最终 SSE 的汇合点。

**Step 1: Set verification path**

覆盖每次模型调用前预算、冻结工具复用、工具 PENDING/终态、观察后重预算、中间文本不外流、最大步骤、重复调用、取消和最终提交失败。

**Step 2: Prepare verification**

先证明当前 AgentLoop 会把工具步骤文本外流且写库失败仍 COMPLETE，再改为最终步骤缓冲和提交后完成。

**Step 3: Write minimal implementation**

Harness 获取租约并创建 Turn，循环只接受 Prepared Context；模型事件按步骤缓冲，只有无工具步骤提交最终正文；工具结果区分 observation、resultSummary、reference 和错误。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=AgentHarnessTest,AgentLoopTest,AgentChatExecutionServiceTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录治理关卡、失败路径、持久化顺序、取消传播和最终事件顺序。

### Task 9: 接入 Wind MCP 客户端和金融能力 Gateway

**Files:**
- Create: `main/java/com/example/chat/common/enums/WindServerType.java`
- Create: `main/java/com/example/chat/common/enums/FinancialProviderMode.java`
- Create: `main/java/com/example/chat/common/dto/wind/WindToolCallRequest.java`
- Create: `main/java/com/example/chat/common/dto/wind/WindToolCallResult.java`
- Create: `main/java/com/example/chat/integration/client/WindMcpClient.java`
- Create: `main/java/com/example/chat/integration/client/WebClientWindMcpClient.java`
- Create: `main/java/com/example/chat/integration/client/WindCredentialProvider.java`
- Create: `main/java/com/example/chat/integration/client/EnvironmentWindCredentialProvider.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/FinancialDocumentGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/FinancialDataGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/LegacyFinancialDocumentGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/LegacyFinancialDataGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/WindFinancialDocumentGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/WindFinancialDataGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/CompositeFinancialDocumentGateway.java`
- Create: `main/java/com/example/chat/agent/tool/gateway/CompositeFinancialDataGateway.java`
- Modify: `main/java/com/example/chat/agent/tool/RagAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/DpuAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolErrorCode.java`
- Verify: `test/java/com/example/chat/integration/client/WebClientWindMcpClientTest.java`
- Verify: `test/java/com/example/chat/agent/tool/gateway/FinancialGatewayRoutingTest.java`

**Risk Level:** High-risk TDD
**Why:** 金融数据单位、来源、配额、错误和能力范围不能通过模糊 fallback 掩盖。

**Step 1: Set verification path**

覆盖 initialize、tools/list、tools/call、JSON/SSE、Bearer、server/tool allowlist、INVALID→null、单位/警告/来源、auth/rate/quota/backend error 和超时。

**Step 2: Prepare verification**

路由测试必须断言：公告/新闻走 Wind financial_docs，研报/背景走 Legacy RAG，专项数据按资产/意图路由，跨标的复合计算才走 analytics。

**Step 3: Write minimal implementation**

端点只能由 `WindServerType` 构造；模型不能传 URL 或任意 toolName；Provider 模式显式，Wind 关键错误不静默降级；Key 不进入 DTO、日志或记忆。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=WebClientWindMcpClientTest,FinancialGatewayRoutingTest,RagAgentToolTest,DpuAgentToolTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录支持的 server/tool、路由矩阵、错误映射、来源/单位保留和未接入 Alice 的边界。

### Task 10: 增加 OpenAI Responses 风格的服务入口和身份边界

**Files:**
- Create: `main/java/com/example/chat/web/vo/OpenAiResponseRequestVO.java`
- Create: `main/java/com/example/chat/web/controller/OpenAiResponsesController.java`
- Create: `main/java/com/example/chat/service/interf/RequestIdentityResolver.java`
- Create: `main/java/com/example/chat/service/implement/HeaderRequestIdentityResolver.java`
- Modify: `main/java/com/example/chat/web/controller/ChatController.java`
- Modify: `main/java/com/example/chat/web/vo/ChatRequestVO.java`
- Modify: `main/java/com/example/chat/task/TaskCancellationService.java`
- Verify: `test/java/com/example/chat/web/controller/OpenAiResponsesControllerTest.java`
- Verify: `test/java/com/example/chat/web/controller/ChatControllerSecurityTest.java`

**Risk Level:** High-risk TDD
**Why:** 身份伪造、跨用户取消和协议兼容属于外部安全边界。

**Step 1: Set verification path**

覆盖 developer/user input、stream true/false、Responses SSE 类型、缺身份、跨用户 session/task、取消所有权和旧请求兼容。

**Step 2: Prepare verification**

验证生产配置不能信任请求体 userId；local profile 可显式开启兼容 fallback，但必须有告警指标。

**Step 3: Write minimal implementation**

Controller 只解析/校验/调用 Service；数据库 ID 不以 Long 暴露；UI 专属事件仅保留在旧 endpoint，Responses endpoint 输出标准文本/完成/错误事件。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=OpenAiResponsesControllerTest,ChatControllerSecurityTest test`
Expected: FAIL first, then PASS

**Step 5: Record a local checkpoint**

记录两个入口的协议差异、身份来源和取消授权规则。

### Task 11: 增加恢复、指标与结构化上下文状态

**Files:**
- Create: `main/java/com/example/chat/agent/memory/ConversationExecutionRecoveryService.java`
- Create: `main/java/com/example/chat/agent/memory/ConversationMemoryMetrics.java`
- Modify: `main/java/com/example/chat/common/dto/ChatEvent.java`
- Modify: `main/java/com/example/chat/common/enums/ChatEventType.java`
- Modify: `main/java/com/example/chat/service/implement/AgentChatExecutionService.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationExecutionRecoveryServiceTest.java`
- Verify: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`

**Risk Level:** Standard lightweight verification
**Why:** 主要是恢复、监控和前端状态补充，但必须保持终态与错误互斥。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

覆盖陈旧 PROCESSING、REQUESTING、PENDING/RUNNING、过期租约和 compression PENDING；验证 RESET_RECOMMENDED 在 COMPLETE 前只发送一次。

**Step 3: Write minimal implementation**

记录分段 Token、估算误差、SAFE/RISK/REJECT、压缩、工具裁剪、模型/数据库耗时；日志不得含正文、Key 或原始工具参数。

**Step 4: Run chosen verification**

Run: `mvn -f ../pom.xml -Dtest=ConversationExecutionRecoveryServiceTest,AgentChatExecutionServiceTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录恢复条件、指标名、终态事件和脱敏检查结果。

### Task 12: 全量回归、启动验收和运行文档

**Files:**
- Create: `test/java/com/example/chat/agent/memory/ConversationMemoryAcceptanceTest.java`
- Create: `test/java/com/example/chat/agent/memory/ConversationCompressionConcurrencyTest.java`
- Create: `docs/agent-platform-operations.md`
- Modify: `main/resources/application.yml`
- Verify: `../pom.xml`

**Risk Level:** High-risk TDD
**Why:** 只有端到端场景能证明 100 轮压缩、并发 fencing、模型/工具循环和持久化不变量共同成立。

**Step 1: Set verification path**

构造 100 轮、多次普通/深度压缩、两个并发发布者、重复 taskId、模型/工具/数据库部分失败、取消和恢复场景。

**Step 2: Prepare verification**

使用 Java 21 固定运行时执行全量测试；配置 MockWebServer 和 Local Store，真实 MySQL/Wind 只在提供外部环境后运行。

**Step 3: Write minimal implementation**

补齐测试暴露的最小缺口；文档说明环境变量、local/mysql profile、Wind 模式、DDL、API 示例、健康检查、回滚和数据保留边界。

**Step 4: Run chosen verification**

Run: `$env:JAVA_HOME='D:\rsqu\software\IntelliJ IDEA 2025.1\jbr'; & 'D:\rsqu\software\maven3.9.9\bin\mvn.cmd' -f '..\pom.xml' test`
Expected: all tests PASS

Run: `$env:JAVA_HOME='D:\rsqu\software\IntelliJ IDEA 2025.1\jbr'; & 'D:\rsqu\software\maven3.9.9\bin\mvn.cmd' -f '..\pom.xml' package`
Expected: BUILD SUCCESS

Run: `rg -n "P053102|P052184|P052186|promptID|pkey|PKey|Bearer [A-Za-z0-9+/=]{16,}|DEBUG_DUMP|System\.out|printStackTrace" main test`
Expected: no production violations

**Step 5: Record a local checkpoint**

汇总测试数、构建结果、已验证能力、外部未验证项和运行命令；不执行 Git 提交，除非用户另行要求。

## 6. 上线策略

1. **安全切断阶段**：先移除旧 Prompt ID、硬编码凭据、Mock 生产暴露和 DEBUG_DUMP，启用新 Responses 路由但仍使用 Local Store。
2. **影子持久化阶段**：MySQL 双写 Turn/model usage，读取仍由旧内存快照对照，监控估算误差和状态一致性。
3. **上下文灰度阶段**：启用 MySQL 读取、新 Prompt/Context Compiler 和普通压缩，深度压缩只记录决策。
4. **完整治理阶段**：启用 fencing、深度压缩、RESET_RECOMMENDED、恢复任务和 OpenAI Responses 外部入口。
5. **Wind 灰度阶段**：`SHADOW → WIND_PREFERRED → WIND_ONLY`，按文档、行情、财务和 analytics 分能力验收，不做全局一次性切换。

回滚时关闭新读取/压缩/Wind Provider 开关，不删除已写入的 Turn、usage 或摘要；原始 Turn 始终可重新生成摘要。

## 7. 参考依据

- `docs/plans/2026-08-25-agent-conversation-memory-persistence-and-compaction.md`
- `https://aifinmarket.wind.com.cn/skill.md`
- `https://github.com/Wind-Information-Co-Ltd/wind-skills`
- `https://developers.openai.com/api/docs/guides/migrate-to-responses`
- `https://developers.openai.com/api/docs/guides/function-calling`
- `https://developers.openai.com/api/docs/guides/conversation-state`
- `https://developers.openai.com/api/docs/guides/streaming-responses`
