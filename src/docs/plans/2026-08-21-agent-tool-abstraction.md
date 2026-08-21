# Agent Tool Abstraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Agent 工具升级为强类型、自动 Schema、统一校验、结构化错误、审计和权限控制的工具调用体系。

**Architecture:** 保留现有 Reactor ReAct 循环和 OpenAI 兼容协议，在工具层引入泛型 `AgentTool<I>` 与统一调用器。工具只声明定义、输入类型、元数据和业务 `call` 方法；调用器集中完成 JSON 反序列化、Bean Validation、权限判定、超时、审计和错误包装。

**Tech Stack:** Java 21、Spring Boot 3、WebFlux/Reactor、FastJSON、Jakarta Bean Validation、JUnit 5、Mockito

---

### Task 1: 建立强类型工具契约与自动 Schema

**Files:**
- Modify: `main/java/com/example/chat/agent/tool/AgentTool.java`
- Create: `main/java/com/example/chat/agent/tool/AbstractAgentTool.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolSchemaGenerator.java`
- Create: `main/java/com/example/chat/common/annotation/AgentToolParam.java`
- Modify: `main/java/com/example/chat/agent/model/AgentToolDefinition.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -DskipTests compile`

**Risk Level:** High-risk TDD
**Why:** 工具名称、Schema 与运行时入参类型的关联属于核心协议，错误会造成模型调用失败。

**Step 1: Set verification path**

为 Schema 生成器添加定向单元测试，覆盖字段类型、必填约束、长度限制和禁止额外字段。

**Step 2: Prepare verification**

先更新测试表达新接口契约，并确认旧实现不再满足编译要求。

**Step 3: Write minimal implementation**

使用 `AgentTool<I>` 暴露 `definition()`、`inputType()`、`metadata()` 和 `call(I, AgentToolContext)`；名称只从 `definition.function.name` 读取。通过反射读取强类型输入字段及 Jakarta Validation 注解，生成 JSON Schema。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentToolSchemaGeneratorTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录强类型工具协议和 Schema 测试通过结果。

### Task 2: 增加统一调用、校验、错误、审计和权限

**Files:**
- Create: `main/java/com/example/chat/agent/tool/AgentToolContext.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolMetadata.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolError.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolErrorCode.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolPermissionService.java`
- Create: `main/java/com/example/chat/agent/tool/DefaultAgentToolPermissionService.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolAuditService.java`
- Create: `main/java/com/example/chat/agent/tool/Slf4jAgentToolAuditService.java`
- Create: `main/java/com/example/chat/agent/tool/AgentToolInvoker.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolResult.java`
- Modify: `main/java/com/example/chat/agent/tool/AgentToolRegistry.java`
- Modify: `main/java/com/example/chat/config/AgentProperties.java`
- Modify: `main/resources/application.yml`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentToolRegistryTest,AgentToolInvokerTest test`

**Risk Level:** High-risk TDD
**Why:** 权限和参数校验必须在业务调用前可靠执行，错误结果还必须稳定回填模型。

**Step 1: Set verification path**

覆盖未知工具、无权限、JSON 错误、Bean Validation 失败、成功调用和超时审计。

**Step 2: Prepare verification**

先创建调用器测试，并确认缺少实现时测试失败。

**Step 3: Write minimal implementation**

调用器按固定顺序执行：解析工具、鉴权、反序列化、校验、调用、超时、结构化错误、审计。权限默认要求存在用户标识，并支持配置级工具启停。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=AgentToolRegistryTest,AgentToolInvokerTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录统一治理链路和针对性测试结果。

### Task 3: 迁移 RAG、DPU、NER 工具

**Files:**
- Create: `main/java/com/example/chat/common/dto/agent/tool/SearchFinancialDocumentsInput.java`
- Create: `main/java/com/example/chat/common/dto/agent/tool/QueryFinancialDataInput.java`
- Create: `main/java/com/example/chat/common/dto/agent/tool/ResolveFinancialEntityInput.java`
- Modify: `main/java/com/example/chat/agent/tool/RagAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/DpuAgentTool.java`
- Modify: `main/java/com/example/chat/agent/tool/ResolveEntityAgentTool.java`
- Modify: `test/java/com/example/chat/agent/tool/RagAgentToolTest.java`
- Modify: `test/java/com/example/chat/agent/tool/DpuAgentToolTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=RagAgentToolTest,DpuAgentToolTest test`

**Risk Level:** Standard lightweight verification
**Why:** 下游调用语义保持不变，主要是适配新契约。

**Step 1: Set verification path**

Skip strict TDD; use lightweight verification.

**Step 2: Prepare verification**

迁移现有工具测试，使其使用强类型入参与 `call` 方法。

**Step 3: Write minimal implementation**

删除工具内部 JSON 解析和重复必填校验；工具仅处理已校验输入及下游结果转换，并实际消费 DPU 的指标和时间范围字段。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml -Dtest=RagAgentToolTest,DpuAgentToolTest test`
Expected: PASS

**Step 5: Record a local checkpoint**

记录三个工具完成强类型迁移。

### Task 4: 接入 ReAct 循环并回归验证

**Files:**
- Modify: `main/java/com/example/chat/agent/AgentLoop.java`
- Modify: `test/java/com/example/chat/agent/AgentLoopTest.java`
- Modify: `test/java/com/example/chat/service/implement/AgentChatExecutionServiceTest.java`
- Verify: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`

**Risk Level:** High-risk TDD
**Why:** ReAct 消息回填和循环终止是 Agent 主链路，必须防止行为回归。

**Step 1: Set verification path**

保留直接文本、工具调用后回答、未知工具、重复调用和终止事件测试。

**Step 2: Prepare verification**

更新 AgentLoop 测试使用统一调用器，并验证结构化错误可作为工具消息回填。

**Step 3: Write minimal implementation**

AgentLoop 仅负责循环和事件编排；工具发现、鉴权、校验、调用和审计全部委托给 AgentToolInvoker。

**Step 4: Run chosen verification**

Run: `D:\rsqu\software\maven3.9.9\bin\mvn.cmd -f ..\pom.xml test`
Expected: BUILD SUCCESS

**Step 5: Record a local checkpoint**

记录全量测试结果，并整理新工具调用链讲解。
