package com.example.chat.common.dto.wind;

import com.example.chat.common.enums.WindServerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Wind MCP 工具发现结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindToolCatalogResult {

    /** Wind MCP 服务类型。 */
    private WindServerType serverType;
    /** 通过本地白名单过滤后的工具定义。 */
    private List<WindToolDefinition> tools;
}
