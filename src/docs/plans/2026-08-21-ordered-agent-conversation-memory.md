# Ordered Agent Conversation Memory Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 使用 OpenAI Chat Completions 标准消息块组织上下文，并按真实调用顺序持久化工具轨迹。

**Architecture:** 继续使用 `AgentMessage` 作为协议消息块，字段保持 `role`、`content`、`tool_calls`
和 `tool_call_id`。单轮执行上下文额外收集需要持久化的消息，其中工具结果只保存状态；短期模型调用仍使用
完整工具结果。会话记忆按轮保存有序消息列表，提示词工厂按原顺序回放。

**Tech Stack:** Java 21、Spring WebFlux、FastJSON、Lombok、JUnit 5、Reactor Test

---

### Task 1: 标准化协议消息块

**Files:**
- Modify: `main/java/com/example/chat/agent/model/AgentMessage.java`
- Modify: `main/java/com/example/chat/agent/prompt/AgentPromptFactory.java`
- Verify: `test/java/com/example/chat/agent/prompt/AgentPromptFactoryTest.java`

**Risk Level:** Standard lightweight verification
**Why:** 消息字段已有基础实现，主要调整角色语义和标准字段。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

更新提示词测试，验证固定规则使用 `developer`，历史消息按协议顺序回放，页面上下文与当前问题组成用户输入。

**Step 3: Write minimal implementation**

增加 `developer` 工厂方法；删除 `tool` 消息的非标准 `name` 字段；保留协议字段 `content` 而不是自定义
`context`。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=AgentPromptFactoryTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录协议消息结构和验证结果，不执行 Git 操作。

### Task 2: 按顺序记录单轮工具轨迹

**Files:**
- Modify: `main/java/com/example/chat/agent/AgentTurnContext.java`
- Modify: `main/java/com/example/chat/agent/AgentLoop.java`
- Modify: `main/java/com/example/chat/service/implement/AgentChatExecutionService.java`
- Verify: `test/java/com/example/chat/agent/AgentLoopTest.java`
- Verify: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`

**Risk Level:** High-risk TDD
**Why:** 工具调用与结果必须严格配对且保持顺序，错误会导致后续模型请求不符合协议。

**Step 1: Set verification path**

先增加断言验证 `user -> assistant.tool_calls -> tool -> assistant` 的持久化顺序。

**Step 2: Prepare verification**

工具执行使用完整观察结果继续模型调用，持久化副本仅保留 `SUCCESS`、`FAILED` 或 `SKIPPED` 状态。

**Step 3: Write minimal implementation**

在 `AgentTurnContext` 中增加当前轮持久化消息列表，AgentLoop 每产生工具调用或结果时同步追加，最终回答完成后
追加 assistant 消息并交给记忆服务。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=AgentLoopTest,AgentChatExecutionServiceTest test`
Expected: FAIL first then PASS

**Step 5: Record a local checkpoint**

记录有序轨迹和状态压缩验证结果，不执行 Git 操作。

### Task 3: 将会话轮次迁移为有序消息列表

**Files:**
- Modify: `main/java/com/example/chat/agent/memory/ConversationTurn.java`
- Modify: `main/java/com/example/chat/agent/memory/ConversationMemoryService.java`
- Modify: `main/java/com/example/chat/agent/memory/ConversationSummaryService.java`
- Modify: `main/java/com/example/chat/agent/memory/InMemoryConversationMemoryRepository.java`
- Verify: `test/java/com/example/chat/agent/memory/ConversationMemoryServiceTest.java`

**Risk Level:** High-risk TDD
**Why:** 这是会话持久化模型变更，必须保证窗口淘汰、摘要和会话隔离行为不回退。

**Step 1: Set verification path**

先将记忆测试改为验证每轮的有序消息集合和状态型工具结果。

**Step 2: Prepare verification**

保留三轮滑动窗口、摘要合并和用户会话隔离测试。

**Step 3: Write minimal implementation**

`ConversationTurn` 改为持有 `List<AgentMessage>`；摘要服务从第一条 user 和最后一条纯文本 assistant 提取摘要；
仓储复制完整消息列表。

**Step 4: Run chosen verification**

Run: `mvn -Dtest=ConversationMemoryServiceTest test`
Expected: FAIL first then PASS

**Step 5: Record a local checkpoint**

记录记忆模型迁移和测试结果，不执行 Git 操作。

### Task 4: 全量回归

**Files:**
- Verify: `pom.xml`

**Risk Level:** Standard lightweight verification
**Why:** 改动横跨消息协议、Agent 循环和记忆服务，需要完整回归。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

检查 Java 文件编码、差异空白和修改文件行宽。

**Step 3: Write minimal implementation**

只修复验证发现的问题，不扩大功能范围。

**Step 4: Run chosen verification**

Run: `mvn test`
Expected: 全部测试通过

**Step 5: Record a local checkpoint**

汇总协议差异、实现结果和验证数量，不执行 Git 操作。
