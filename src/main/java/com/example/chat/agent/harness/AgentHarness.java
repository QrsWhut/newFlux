package com.example.chat.agent.harness;

import com.example.chat.agent.AgentLoop;
import com.example.chat.agent.AgentTurnContext;
import com.example.chat.common.dto.ChatEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Harness 治理入口，统一包装受治理的 ReAct 循环。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Component
public class AgentHarness {

    /** 受治理的 ReAct 循环。 */
    private final AgentLoop agentLoop;

    /**
     * 创建 Agent Harness。
     *
     * @param agentLoop ReAct 循环
     */
    public AgentHarness(AgentLoop agentLoop) {
        this.agentLoop = agentLoop;
    }

    /**
     * 执行单轮受治理 Agent 请求。
     *
     * @param context 单轮执行上下文
     * @param sequence 事件序号生成器
     * @return 业务事件流
     */
    public Flux<ChatEvent> run(AgentTurnContext context, AtomicLong sequence) {
        return agentLoop.run(context, sequence);
    }
}
