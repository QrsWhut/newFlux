# Agent Tool Schema Annotations Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将工具名称和说明迁移到显式注解，并生成符合 OpenAI strict function calling 约束的参数 Schema。

**Architecture:** 工具类通过注解声明名称和用途，输入 DTO 字段通过说明注解与 Jakarta Validation 声明参数语义。Schema 生成器只负责读取这些显式元数据，不推测业务说明；运行时仍由 AgentToolInvoker 统一校验。

**Tech Stack:** Java 21、Spring Boot、FastJSON、Jakarta Validation、OpenAI-compatible Function Calling

---

### Task 1: 工具级定义注解

**Files:**
- Create: `main/java/com/example/chat/agent/tool/annotation/AgentToolSpec.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolSchemaGenerator.java`
- Modify: `main/java/com/example/chat/agent/tool/AbstractAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/*AgentTool.java`
- Verify: `mvn -Dtest=AgentToolSchemaGeneratorTest test`

**Risk Level:** High-risk TDD
**Why:** 工具名称和说明属于模型路由协议，错误会导致模型选择错误工具。

**Step 1: Set verification path**

测试注解中的 name 和 description 能稳定进入工具定义。

**Step 2: Prepare verification**

先更新 Schema 测试表达注解契约。

**Step 3: Write minimal implementation**

新增运行时类型注解并删除各工具构造器中的名称和说明字符串。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=AgentToolSchemaGeneratorTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录工具元数据已由显式注解统一提供。

### Task 2: strict Schema 与流式协议回归

**Files:**
- Modify: `main/java/com/example/chat/agent/model/AgentToolDefinition.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolSchemaGenerator.java`
- Modify: `test/java/com/example/chat/agent/client/WebClientAgentLlmClientTest.java`
- Verify: `mvn test`

**Risk Level:** High-risk TDD
**Why:** strict Schema 对 required、nullable 和 additionalProperties 有组合约束。

**Step 1: Set verification path**

验证 strict=true、全部字段进入 required、业务可选字段允许 null，并保留流式参数分片聚合。

**Step 2: Prepare verification**

扩展现有 Schema 与流式 tool_calls 测试。

**Step 3: Write minimal implementation**

生成 OpenAI strict 兼容 Schema；继续缓冲流式参数 delta，只在完成标志后执行。

**Step 4: Run chosen verification**

Run: `mvn test`
Expected: BUILD SUCCESS

**Step 5: Record a local checkpoint**

记录协议约束和完整回归结果。
