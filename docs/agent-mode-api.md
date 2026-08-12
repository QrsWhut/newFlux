# 双模式金融对话 API 与 ReAct Agent 使用说明书

本文档描述金融对话服务双模式（`WORKFLOW` / `AGENT`）的接口协议、入参字段及 ReAct Agent 的执行行为。

---

## 1. 接口概述

接口 URL: `/api/chat/stream`
请求方式: `POST`
响应类型: `text/event-stream` (Server-Sent Events)

## 2. 请求参数 (ChatRequestVO)

| 字段名 | 类型 | 是否必填 | 默认值 | 描述 |
|---|---|---|---|---|
| `sessionId` | String | 是 | - | 会话唯一 ID |
| `userId` | String | 是 | - | 用户唯一 ID |
| `question` | String | 是 | - | 用户输入的原始提问内容 |
| `executionMode` | Enum | 否 | `WORKFLOW` | 执行模式：`WORKFLOW` (固定编排) 或 `AGENT` (ReAct 自主 Agent) |
| `mode` | Integer | 否 | 1 | 【已过时】旧版模式映射：`1 -> WORKFLOW`, `2 -> AGENT` |
| `history` | Array | 否 | `[]` | 客户端前端的历史消息列表（AGENT 模式服务端自主管理三轮记忆） |
| `pageData` | String | 否 | `""` | 页面携带数据上下文 |

### 请求 Payload 示例 (Agent 模式)

```json
{
  "sessionId": "sess_88888888",
  "userId": "user_9999",
  "question": "贵州茅台最近表现怎么样？",
  "executionMode": "AGENT",
  "pageData": "当前在看股票: 600519"
}
```

---

## 3. SSE 输出事件流规范

服务器按产生顺序持续推送 SSE 数据块，每块包含 `event`（事件名）、`id`（序号）及 `data`（JSON 数据）：

### 事件类型 (`ChatEventType`)

1. **`STATUS`**: 表示后台或工具的状态更新（如：“正在查询 queryFinancialData...”）
2. **`UI_UPDATE`**: 前端卡片渲染数据（如 `rag-card`、`dpu-card`、`ner-card`）
3. **`TEXT_DELTA`**: 助手回答文本切片增量 (`content`)
4. **`COMPLETE`**: 当前轮对话流式输出彻底完成
5. **`ERROR`**: 链路产生严重错误

---

## 4. ReAct Agent 执行模式策略

1. **单请求生命周期**：Agent 在单次 HTTP 链接内循环。若信息不足，直接生成澄清文本并以 `COMPLETE` 完成，不存在 `WAITING_FOR_USER` 阻塞。
2. **三轮滑动窗口记忆**：服务端自动根据 `userId + sessionId` 维持“历史摘要 + 最近三轮完整问答”，超出 3 轮的早早期对话会被合并压缩为中文字段摘要。
3. **受限白名单工具**：
   - `searchFinancialDocuments` (RAG 检索)
   - `queryFinancialData` (DPU 行情指标)
   - `resolveFinancialEntity` (NER 实体解析)
4. **硬边界控制**：最多 4 次模型决策回合，工具调用单次超时 10 秒，同参数调用自动去重。
