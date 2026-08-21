package com.example.chat.agent.tool;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * 工具调用上下文，承载身份与链路信息，不接受模型覆盖。
 */
@Value
@Builder
public class AgentToolContext {

    /** 任务标识。 */
    String taskId;
    /** 会话标识。 */
    String sessionId;
    /** 可信用户标识。 */
    String userId;
    /** 非模型生成的业务属性。 */
    Map<String, Object> attributes;
}
