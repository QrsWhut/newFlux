package com.example.chat.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具参数说明，用于生成提供给模型的 JSON Schema。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolParam {

    /**
     * 参数用途、格式和取值语义。
     *
     * @return 参数说明
     */
    String description();
}
