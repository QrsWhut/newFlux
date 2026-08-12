package com.example.chat.agent.tool;

import com.example.chat.agent.model.AgentToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 受限 Agent 工具白名单注册表
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Slf4j
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolMap;
    private final List<AgentToolDefinition> toolDefinitions;

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> map = new HashMap<>();
        List<AgentToolDefinition> definitions = new ArrayList<>();

        if (tools != null) {
            for (AgentTool tool : tools) {
                if (map.containsKey(tool.name())) {
                    throw new IllegalStateException("重复注册 Agent 工具: " + tool.name());
                }
                map.put(tool.name(), tool);
                definitions.add(tool.definition());
            }
        }

        this.toolMap = Collections.unmodifiableMap(map);
        this.toolDefinitions = Collections.unmodifiableList(definitions);
        log.info("AgentToolRegistry 初始化完成，已注册白名单工具: {}", toolMap.keySet());
    }

    /**
     * 根据工具名查找受限工具
     *
     * @param toolName 工具名
     * @return AgentTool 实例，若未注册则返回 Optional.empty()
     */
    public Optional<AgentTool> getTool(String toolName) {
        return Optional.ofNullable(toolMap.get(toolName));
    }

    /**
     * 获取所有可用工具的 OpenAI Schema 定义列表
     *
     * @return 工具 Schema 列表
     */
    public List<AgentToolDefinition> getDefinitions() {
        return toolDefinitions;
    }
}
