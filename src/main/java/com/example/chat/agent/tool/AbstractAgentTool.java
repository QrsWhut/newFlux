package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;

/**
 * Agent 工具基础实现。
 *
 * @param <I> 工具输入类型
 */
public abstract class AbstractAgentTool<I> implements AgentTool<I> {

    /** 工具定义。 */
    private final AgentToolDefinition definition;
    /** 强类型输入。 */
    private final Class<I> inputType;
    /** 运行元数据。 */
    private final AgentToolMetadata metadata;

    /**
     * 创建基础工具。
     *
     * @param inputType 输入类型
     * @param metadata 运行元数据
     * @param schemaGenerator Schema 生成器
     */
    protected AbstractAgentTool(Class<I> inputType, AgentToolMetadata metadata,
            AgentToolSchemaGenerator schemaGenerator) {
        this.definition = schemaGenerator.createDefinition(getClass(), inputType);
        this.inputType = inputType;
        this.metadata = metadata;
    }

    @Override
    public final AgentToolDefinition definition() {
        return definition;
    }

    @Override
    public final Class<I> inputType() {
        return inputType;
    }

    @Override
    public final AgentToolMetadata metadata() {
        return metadata;
    }
}
