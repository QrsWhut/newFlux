package com.example.chat.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 不发送给模型的工具运行元数据。
 */
@Data
@AllArgsConstructor
public class AgentToolMetadata {

    /** 是否要求调用方具有有效用户身份。 */
    private boolean authenticatedUserRequired;

    /** 是否记录工具调用审计日志。 */
    private boolean auditEnabled;

    /**
     * 创建要求登录且开启审计的只读工具元数据。
     *
     * @return 工具元数据
     */
    public static AgentToolMetadata authenticatedReadOnly() {
        return new AgentToolMetadata(true, true);
    }
}
