package com.example.chat.common.dto.wind;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wind MCP 内容块。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindContent {

    /** 内容类型。 */
    private String type;
    /** 文本内容。 */
    private String text;
}
