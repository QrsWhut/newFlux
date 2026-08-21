package com.example.chat.common.dto.agent.tool;

import com.example.chat.common.annotation.AgentToolParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 金融文档检索工具输入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchFinancialDocumentsInput {

    /** 检索词。 */
    @NotBlank(message = "query 不能为空")
    @Size(max = 500)
    @AgentToolParam(description = "金融研报、新闻或背景文档的检索词")
    private String query;
}
