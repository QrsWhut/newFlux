# Wind MCP 与金融 Provider 适配层 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在保留现有 RAG/DPU 工具与客户端的前提下，增加安全、可测试的 Wind MCP 原生适配层，并用可配置 Provider 组合接入现有 Agent 工具。

**Architecture:** 使用固定枚举维护 Wind MCP 端点和工具白名单，Java WebClient 按 `initialize -> tools/list|tools/call` 调用 JSON-RPC，并统一解析 JSON/SSE。现有 Agent 工具改为依赖金融 Gateway；Composite Gateway 默认只走 Legacy，切换到 Wind 时按业务域路由，并禁止认证、限流和额度错误静默回退。

**Tech Stack:** Java 21、Spring Boot 3.3、Spring WebFlux、Project Reactor、FastJSON、JUnit 5、Mockito、MockWebServer

---

### Task 1: Wind 配置、枚举与 DTO

**Files:**
- Create: `main/java/com/example/chat/config/WindProperties.java`
- Create: `main/java/com/example/chat/common/enums/WindServerType.java`
- Create: `main/java/com/example/chat/common/enums/FinancialProviderMode.java`
- Create: `main/java/com/example/chat/common/enums/FinancialProvider.java`
- Create: `main/java/com/example/chat/common/enums/FinancialProviderErrorCode.java`
- Create: `main/java/com/example/chat/common/dto/wind/*.java`
- Create: `main/java/com/example/chat/common/dto/financial/*.java`

**Risk Level:** Standard lightweight verification
**Why:** 主要是强类型边界和无副作用配置。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

使用 Maven test-compile 验证 DTO、Lombok 和配置绑定类型可编译。

**Step 3: Write minimal implementation**

端点只允许由 `WindServerType` 枚举提供；`WindProperties` 未配置时返回 `LEGACY_ONLY`，不包含 API Key 字段。

**Step 4: Run chosen verification**

Run: `mvn -DskipTests test-compile`
Expected: `BUILD SUCCESS`

**Step 5: Record a local checkpoint**

记录新增类型和编译结果，不执行 Git 操作。

### Task 2: 凭据与原生 MCP 客户端

**Files:**
- Create: `main/java/com/example/chat/integration/client/WindCredentialProvider.java`
- Create: `main/java/com/example/chat/integration/client/EnvironmentWindCredentialProvider.java`
- Create: `main/java/com/example/chat/integration/client/WindMcpClient.java`
- Create: `main/java/com/example/chat/integration/client/WebClientWindMcpClient.java`
- Create: `main/java/com/example/chat/common/exception/FinancialProviderException.java`
- Test: `test/java/com/example/chat/integration/client/WebClientWindMcpClientTest.java`

**Risk Level:** High-risk TDD
**Why:** JSON/SSE 双协议解析、错误分类和敏感凭据处理属于核心且回归风险高的边界。

**Step 1: Set verification path**

先写 MockWebServer 测试覆盖 `initialize -> tools/call`、SSE、`INVALID -> null`、警告保留、缺 Key 和 429。

**Step 2: Prepare verification**

Run: `mvn -Dtest=WebClientWindMcpClientTest test`
Expected: 首次因实现缺失而失败。

**Step 3: Write minimal implementation**

实现 Bearer 认证、固定端点、工具白名单、JSON-RPC、JSON/SSE 解析、结构化错误映射和安全结果规范化；任何日志与异常均不携带 Key。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=WebClientWindMcpClientTest test`
Expected: `BUILD SUCCESS`

**Step 5: Record a local checkpoint**

记录客户端协议契约与测试覆盖，不执行 Git 操作。

### Task 3: Provider Gateway 与 Agent 工具接线

**Files:**
- Create: `main/java/com/example/chat/integration/client/FinancialDocumentGateway.java`
- Create: `main/java/com/example/chat/integration/client/FinancialDataGateway.java`
- Create: `main/java/com/example/chat/integration/client/Legacy*Gateway.java`
- Create: `main/java/com/example/chat/integration/client/Wind*Gateway.java`
- Create: `main/java/com/example/chat/integration/client/Composite*Gateway.java`
- Modify: `main/java/com/example/chat/agent/tool/RagAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/DpuAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolErrorCode.java`
- Modify: `main/java/com/example/chat/common/dto/agent/tool/SearchFinancialDocumentsInput.java`
- Modify: `main/java/com/example/chat/common/dto/agent/tool/QueryFinancialDataInput.java`

**Risk Level:** High-risk TDD
**Why:** Provider 选择和错误回退规则直接决定数据来源与认证/额度安全语义。

**Step 1: Set verification path**

先写 Composite Gateway 测试，覆盖默认 Legacy、Wind 优先以及认证/限流/额度错误不得回退。

**Step 2: Prepare verification**

Run: `mvn -Dtest=CompositeFinancialDocumentGatewayTest,CompositeFinancialDataGatewayTest test`
Expected: 首次因实现缺失而失败。

**Step 3: Write minimal implementation**

Legacy 实现包装现有客户端；Wind 文档实现只路由公告/新闻；Wind 数据实现按资产和意图选择专项工具，聚合请求才使用 `analytics_data`。Agent 工具名称保持不变，观察结果带 provider、source、warnings。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=CompositeFinancialDocumentGatewayTest,CompositeFinancialDataGatewayTest,RagAgentToolTest,DpuAgentToolTest test`
Expected: `BUILD SUCCESS`

**Step 5: Record a local checkpoint**

记录 Gateway 路由和兼容结果，不执行 Git 操作。

### Task 4: 完整定向验证与变更审计

**Files:**
- Verify: `main/java/com/example/chat/**`
- Verify: `test/java/com/example/chat/**`

**Risk Level:** Standard lightweight verification
**Why:** 需要确认新增组件没有破坏既有工具抽象，也没有越界修改受保护文件。

**Step 1: Set verification path**

Skip strict TDD; use targeted regression tests and source checks.

**Step 2: Prepare verification**

检查 UTF-8 BOM、中文乱码 `??`、Key 字样日志、变更清单和受保护文件状态。

**Step 3: Write minimal implementation**

仅修复验证发现的问题，不增加未要求的模型、记忆或 WebClient 配置改造。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=WebClientWindMcpClientTest,CompositeFinancialDocumentGatewayTest,CompositeFinancialDataGatewayTest,RagAgentToolTest,DpuAgentToolTest test`
Expected: `BUILD SUCCESS`

**Step 5: Record a local checkpoint**

报告通过的测试、接口契约和需要真实 Wind 环境验证的事项。
