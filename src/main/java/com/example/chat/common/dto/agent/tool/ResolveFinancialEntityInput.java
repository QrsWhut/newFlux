package com.example.chat.common.dto.agent.tool;

import com.example.chat.common.annotation.AgentToolParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 金融实体解析工具输入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolveFinancialEntityInput {

    /** 待解析实体。 */
    @NotBlank(message = "entity 不能为空")
    @Size(max = 200)
    @AgentToolParam(description = "待确认或消除歧义的金融实体名称、简称或代称")
    private String entity;
}
