package com.example.chat.common.dto.wind;

import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.enums.WindServerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wind MCP 工具调用请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindToolCallRequest {

    /** Wind MCP 服务类型。 */
    private WindServerType serverType;
    /** 工具名称。 */
    private String toolName;
    /** 工具业务参数。 */
    private JSONObject arguments;
}
