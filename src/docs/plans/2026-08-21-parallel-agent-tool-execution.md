# Parallel Agent Tool Execution Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 并发执行模型单次返回的独立工具调用，同时保持上下文和长期记忆中的确定性顺序。

**Architecture:** AgentLoop 先顺序完成去重和任务准备，再通过 Reactor `flatMapSequential` 有界并发订阅工具。
工具完成结果不直接修改共享上下文，而是在按模型返回顺序归并时统一追加消息和事件。请求显式声明
`parallel_tool_calls=true`，并通过配置限制最大并发量。

**Tech Stack:** Java 21、Project Reactor、Spring Boot Configuration Properties、JUnit 5、Mockito

---

### Task 1: 增加并发配置和协议开关

**Files:**
- Modify: `main/java/com/example/chat/config/AgentProperties.java`
- Modify: `main/resources/application.yml`
- Modify: `main/java/com/example/chat/agent/client/WebClientAgentLlmClient.java`
- Verify: `test/java/com/example/chat/agent/client/WebClientAgentLlmClientTest.java`

**Risk Level:** Standard lightweight verification
**Why:** 配置和请求字段是局部、可回退的协议增强。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

验证带工具的请求包含 `parallel_tool_calls=true`。

**Step 3: Write minimal implementation**

增加 `maxParallelToolCalls`，默认值为 4；工具请求显式允许模型产生并行调用。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=WebClientAgentLlmClientTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录配置和请求字段验证，不执行 Git 操作。

### Task 2: 并发执行并确定性归并

**Files:**
- Modify: `main/java/com/example/chat/agent/AgentLoop.java`
- Verify: `test/java/com/example/chat/agent/AgentLoopTest.java`

**Risk Level:** High-risk TDD
**Why:** 并发执行涉及共享上下文、消息顺序和响应式订阅时机，容易产生竞态。

**Step 1: Set verification path**

先增加双工具测试，验证两个 Mono 在任一完成前都已订阅。

**Step 2: Prepare verification**

让第二个工具先完成，验证最终 tool 消息仍按照模型返回的 call ID 顺序写入。

**Step 3: Write minimal implementation**

顺序准备调用任务，使用 `flatMapSequential` 有界并发执行，在有序下游阶段追加完整短期结果和状态型长期结果。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=AgentLoopTest test`
Expected: FAIL first then PASS

**Step 5: Record a local checkpoint**

记录并发订阅和确定性顺序测试结果，不执行 Git 操作。

### Task 3: 全量回归

**Files:**
- Verify: `pom.xml`

**Risk Level:** Standard lightweight verification
**Why:** AgentLoop 是核心执行路径，需要验证单工具、失败、超时和记忆功能没有回退。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

检查差异、UTF-8 无 BOM 和测试结果。

**Step 3: Write minimal implementation**

只修复回归问题，不扩大到有依赖工具的 DAG 调度。

**Step 4: Run chosen verification**

Run: `mvn test`
Expected: 全部测试通过

**Step 5: Record a local checkpoint**

汇总执行策略、协议行为和测试数量，不执行 Git 操作。
