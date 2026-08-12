package com.example.chat.agent.tool;

import com.example.chat.common.dto.UiNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果封装类
 *
 * @author Antigravity
 * @since 2026-08-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {

    private String toolName;
    private boolean success;

    /**
     * 供模型在 ReAct 循环中观察的结构化/截断文本
     */
    private String observation;

    /**
     * 可选的前端 UI 渲染卡片节点
     */
    private UiNode uiNode;

    public static AgentToolResult success(String toolName, String observation, UiNode uiNode) {
        return AgentToolResult.builder()
                .toolName(toolName)
                .success(true)
                .observation(observation)
                .uiNode(uiNode)
                .build();
    }

    public static AgentToolResult failure(String toolName, String errorMessage) {
        return AgentToolResult.builder()
                .toolName(toolName)
                .success(false)
                .observation("工具执行异常: " + errorMessage)
                .build();
    }
}
