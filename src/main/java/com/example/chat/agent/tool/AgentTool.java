package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;
import reactor.core.publisher.Mono;

/**
 * Agent 统一工具接口。
 *
 * @param <I> 工具强类型输入
 */
public interface AgentTool<I> {

    /**
     * 获取工具完整定义，工具名称以该定义为唯一来源。
     *
     * @return 工具定义
     */
    AgentToolDefinition definition();

    /**
     * 获取工具输入类型。
     *
     * @return 工具输入类型
     */
    Class<I> inputType();

    /**
     * 获取工具运行元数据。
     *
     * @return 工具运行元数据
     */
    AgentToolMetadata metadata();

    /**
     * 调用工具业务能力。
     *
     * @param input 已完成反序列化和校验的输入
     * @param context 工具调用上下文
     * @return 工具调用结果
     */
    Mono<AgentToolResult> call(I input, AgentToolContext context);
}
