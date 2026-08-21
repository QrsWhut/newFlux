package com.example.chat.agent.tool;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.UiNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果，区分成功观察与结构化错误。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {

    /** 是否成功。 */
    private boolean success;
    /** 提供给模型的业务观察。 */
    private String observation;
    /** 可选前端节点。 */
    private UiNode uiNode;
    /** 失败时的结构化错误。 */
    private AgentToolError error;

    /**
     * 创建成功结果。
     *
     * @param observation 业务观察
     * @param uiNode 前端节点
     * @return 成功结果
     */
    public static AgentToolResult success(String observation, UiNode uiNode) {
        return AgentToolResult.builder()
                .success(true)
                .observation(observation)
                .uiNode(uiNode)
                .build();
    }

    /**
     * 创建失败结果。
     *
     * @param error 结构化错误
     * @return 失败结果
     */
    public static AgentToolResult failure(AgentToolError error) {
        return AgentToolResult.builder()
                .success(false)
                .error(error)
                .build();
    }

    /**
     * 生成回填给模型的稳定结构化观察。
     *
     * @return JSON 观察文本
     */
    public String toModelObservation() {
        JSONObject payload = new JSONObject(true);
        payload.put("success", success);
        if (success) {
            payload.put("observation", observation == null ? "" : observation);
        } else {
            payload.put("error", error);
        }
        return payload.toJSONString();
    }
}
