package com.example.chat.integration.client;

import com.example.chat.common.dto.wind.WindToolCallRequest;
import com.example.chat.common.dto.wind.WindToolCallResult;
import com.example.chat.common.dto.wind.WindToolCatalogResult;
import com.example.chat.common.enums.WindServerType;
import reactor.core.publisher.Mono;

/**
 * Wind MCP 原生客户端接口。
 */
public interface WindMcpClient {

    /**
     * 获取指定 Wind MCP 服务当前公开的工具定义。
     *
     * @param serverType Wind MCP 服务类型
     * @return 工具发现结果
     */
    Mono<WindToolCatalogResult> listTools(WindServerType serverType);

    /**
     * 调用白名单内的 Wind MCP 工具。
     *
     * @param request 工具调用请求
     * @return 工具调用结果
     */
    Mono<WindToolCallResult> call(WindToolCallRequest request);
}
