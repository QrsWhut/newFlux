# Agent 平台运行与灰度手册

本文对应 2026-08-25 的服务端 Agent 改造版本。密钥、账号和域名均为占位信息，不得提交到 Git。

## 1. 已实现边界

- 入站：`POST /v1/responses` 支持 JSON 非流式和 OpenAI Responses 风格 SSE 子集。
- 兼容：`POST /api/chat/stream`、`POST /api/chat/cancel`；旧 `WORKFLOW` 请求统一进入 Agent Harness。
- 身份：业务入口不信任请求体 `userId`，必须校验受信网关 HMAC 请求头。
- 模型：出站统一使用 OpenAI Responses API，支持 provider/model、API Key、函数调用和 usage。
- 上下文：Prompt 由项目内版本化资源管理；客户端历史不会覆盖服务端事实。
- Harness：治理轮数、超时、权限、并发、重复调用、结果截断和每步 Token 预算。
- 记忆：支持 `local`/`mysql`，保存 Turn、脱敏工具事实、模型调用、usage、租约和 fencing epoch。
- 金融：保留 Legacy RAG/DPU/NER；Wind MCP 作为可灰度 Provider 接在稳定工具门面之后。
- JSON：WebFlux 使用 FastJSON 编解码，单对象默认限制 1 MiB。

当前明确边界：普通/深度压缩的范围规划、DDL 和预算判定已实现，但摘要模型调用、CAS 发布与发布后重载
尚未接入生产请求。长历史进入 `RISK` 时仍可继续，进入 `REJECT` 时由 Harness 拒绝。补齐前不得宣称
完成百轮长会话验收。

每个 ReAct 模型步骤先完整收集供应商事件，避免同时返回文本和工具调用时误发中间文本；代价是正文首字节
要等待该模型步骤完成，不是严格的逐 token 首字节流式。

## 2. 关键顺序

取得会话租约 → 创建幂等 Turn → 加载服务端快照 → 冻结 Prompt/工具 → 每步预算 → Responses 模型 →
受控工具 → 刷新模型/工具事实 → 条件提交 Turn 终态 → 释放租约。JDBC/MyBatis 在 `boundedElastic` 执行。

## 3. 环境变量

### 模型

| 变量 | 默认值 |
| --- | --- |
| `AI_DEFAULT_PROVIDER` | `openai` |
| `OPENAI_BASE_URL` | `https://api.openai.com` |
| `OPENAI_API_KEY` | 空 |
| `OPENAI_MODEL` | `gpt-5.6-sol` |
| `OPENAI_RESPONSES_PATH` | `/v1/responses` |
| `OPENAI_MAX_CONNECTIONS` | `100` |
| `OPENAI_PENDING_REQUESTS` | `200` |
| `OPENAI_CONNECT_TIMEOUT` | `5s` |
| `OPENAI_RESPONSE_TIMEOUT` | `120s` |

### 身份

`TRUSTED_IDENTITY_HMAC_SECRET` 至少 32 字节。请求头为 `X-User-Id`、`X-Identity-Timestamp` 和
`X-Identity-Signature`。签名是 `HMAC-SHA256(timestamp + "\n" + userId)` 的十六进制值，时间偏差最多 5 分钟。
当前签名未覆盖方法、路径、正文和 nonce，生产网关应叠加 TLS、限流和防重放；后续建议扩展签名载荷。

### 记忆与 MySQL

| 变量 | 默认值 |
| --- | --- |
| `AGENT_MEMORY_STORE_TYPE` | `local` |
| `AGENT_MYSQL_URL` | 空 |
| `AGENT_MYSQL_USERNAME` | 空 |
| `AGENT_MYSQL_PASSWORD` | 空 |
| `AGENT_MYSQL_MAX_POOL_SIZE` | `20` |
| `AGENT_MYSQL_MIN_IDLE` | `2` |
| `AGENT_LEASE_DURATION` | `30s` |
| `AGENT_LEASE_RENEW_INTERVAL` | `10s` |

DDL：`main/resources/db/schema/agent_conversation_memory.sql`。包含会话、Turn、工具调用、摘要和模型调用 5 张表。
生产启用 MySQL 前必须验证字符集、索引、事务、租约抢占、多实例 fencing 和重复 taskId。

### Wind 与 Legacy

Wind 变量：`WIND_PROVIDER_MODE`、`WIND_API_KEY`、`WIND_INITIALIZE_TIMEOUT`、`WIND_CALL_TIMEOUT`。
模式支持 `LEGACY_ONLY`、`WIND_PREFERRED`、`WIND_ONLY`，默认 `LEGACY_ONLY`。
Legacy 地址：`LEGACY_RAG_BASE_URL`、`LEGACY_DPU_BASE_URL`、`LEGACY_NER_BASE_URL`、
`LEGACY_VIEWPOINT_BASE_URL`。认证、配额、限流和非法参数错误不会静默回退。
Wind skill 安装范围仍待选择“当前项目”或“全局”，确认前禁止安装。

## 4. 构建、启动和健康检查

```powershell
$env:JAVA_HOME = "D:\path\to\jdk-21"
mvn clean verify
java -jar ".\target\new-ab1-workflow-1.0.0-SNAPSHOT.jar"
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/actuator/health"
```

应用可在没有模型 Key 和 HMAC Secret 时启动，但业务请求会在模型或身份边界失败。`UP` 不证明外部链路可用。

## 5. 灰度与回滚

Wind 按 `LEGACY_ONLY → WIND_PREFERRED → WIND_ONLY` 分阶段验收公告/新闻、股票、基金、指数、债券、
经济数据和 analytics。回滚到 `LEGACY_ONLY` 并滚动重启，不删除已写入事实。
模型回滚切换默认 provider 后滚动重启。MySQL 降级为 local 会失去持久化和多实例一致性，只能应急使用。

## 6. 发布前外部验收

- 使用真实模型 Key 验证 SSE、函数调用、usage、401/429/5xx、取消和超时。
- 使用授权 Wind Key 验证 7 个 MCP 服务、在线 schema、额度、单位、量级和错误信封。
- 在目标 MySQL 执行 DDL 和并发/故障恢复测试。
- 验证目标网络中的 Legacy RAG/DPU/NER/Viewpoint。
- 补齐自动摘要、CAS 发布、深度压缩、`RESET_RECOMMENDED` 和百轮测试。
- 增加外部依赖 readiness；完成容量、成本、数据留存、密钥轮换和渗透测试。
