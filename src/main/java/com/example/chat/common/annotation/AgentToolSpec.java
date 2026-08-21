package com.example.chat.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具定义，用于生成提供给模型的工具元数据。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolSpec {

    /**
     * 获取工具名称。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 获取工具用途说明。
     *
     * @return 工具用途说明
     */
    String description();
}
