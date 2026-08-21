package com.example.chat.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 受限 Agent 工具注册表，工具名称只读取 definition。
 */
@Slf4j
@Component
public class AgentToolRegistry {

    /** 按 definition 名称索引的不可变工具集合。 */
    private final Map<String, AgentTool<?>> toolMap;

    /**
     * 收集并校验 Spring 容器中的工具。
     *
     * @param tools 工具列表
     */
    public AgentToolRegistry(List<AgentTool<?>> tools) {
        Map<String, AgentTool<?>> registeredTools = new LinkedHashMap<>();
        if (tools != null) {
            for (AgentTool<?> tool : tools) {
                String toolName = getToolName(tool);
                if (registeredTools.putIfAbsent(toolName, tool) != null) {
                    throw new IllegalStateException("重复注册 Agent 工具: " + toolName);
                }
            }
        }
        this.toolMap = Collections.unmodifiableMap(registeredTools);
        log.info("AgentToolRegistry 初始化完成，已注册工具: {}", toolMap.keySet());
    }

    /**
     * 按名称获取工具。
     *
     * @param toolName 工具名称
     * @return 工具
     */
    public Optional<AgentTool<?>> getTool(String toolName) {
        return Optional.ofNullable(toolMap.get(toolName));
    }

    /**
     * 获取全部已注册工具。
     *
     * @return 不可变工具列表
     */
    public List<AgentTool<?>> getTools() {
        return List.copyOf(toolMap.values());
    }

    private String getToolName(AgentTool<?> tool) {
        if (tool == null || tool.definition() == null || tool.definition().getFunction() == null
                || !StringUtils.hasText(tool.definition().getFunction().getName())) {
            throw new IllegalStateException("Agent 工具 definition.function.name 不能为空");
        }
        return tool.definition().getFunction().getName();
    }
}
