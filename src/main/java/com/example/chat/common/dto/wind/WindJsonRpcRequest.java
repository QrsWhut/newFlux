package com.example.chat.common.dto.wind;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wind MCP JSON-RPC 请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindJsonRpcRequest {

    /** JSON-RPC 协议版本。 */
    private String jsonrpc;
    /** 请求唯一标识。 */
    private String id;
    /** JSON-RPC 方法名。 */
    private String method;
    /** 方法参数。 */
    private JSONObject params;
}
