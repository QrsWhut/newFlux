package com.example.chat.common.dto.wind;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wind MCP 工具定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindToolDefinition {

    /** 工具名称。 */
    private String name;
    /** 工具说明。 */
    private String description;
    /** 工具输入 JSON Schema。 */
    private JSONObject inputSchema;
}
