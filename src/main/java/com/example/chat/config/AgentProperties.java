package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Agent 模式运行参数。
 *
 * @param llm Agent 模型调用与循环控制参数
 * @author Antigravity
 * @since 2026-08-12
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(LlmProperties llm) {

    /**
     * 获取最大模型决策回合数。
     *
     * @return 最大模型决策回合数
     */
    public int maxTurns() {
        return llm != null && llm.maxTurns() != null && llm.maxTurns() > 0
                ? llm.maxTurns() : 4;
    }

    /**
     * 获取单任务总超时时间。
     *
     * @return 单任务总超时时间
     */
    public Duration totalTimeout() {
        return llm != null && llm.totalTimeout() != null
                ? llm.totalTimeout() : Duration.ofSeconds(60);
    }

    /**
     * 获取单个工具调用超时时间。
     *
     * @return 单个工具调用超时时间
     */
    public Duration singleCallTimeout() {
        return llm != null && llm.singleCallTimeout() != null
                ? llm.singleCallTimeout() : Duration.ofSeconds(10);
    }

    /**
     * 获取写入模型上下文的工具观察结果最大长度。
     *
     * @return 最大字符数
     */
    public int observationMaxChars() {
        return llm != null && llm.observationMaxChars() != null && llm.observationMaxChars() > 0
                ? llm.observationMaxChars() : 4000;
    }

    /**
     * 获取工具调用最大并发数。
     *
     * @return 工具调用最大并发数
     */
    public int maxParallelToolCalls() {
        return llm != null && llm.maxParallelToolCalls() != null
                && llm.maxParallelToolCalls() > 0 ? llm.maxParallelToolCalls() : 4;
    }

    /**
     * 获取启用的工具名称；空集合表示启用全部已注册工具。
     *
     * @return 启用的工具名称
     */
    public Set<String> enabledTools() {
        return llm != null && llm.enabledTools() != null
                ? Set.copyOf(llm.enabledTools()) : Collections.emptySet();
    }

    /**
     * Agent 模型调用与循环控制参数。
     *
     * @param maxTurns 最大模型决策回合数
     * @param totalTimeout 单任务总超时时间
     * @param singleCallTimeout 单个工具调用超时时间
     * @param observationMaxChars 工具观察结果最大字符数
     * @param enabledTools 启用的工具名称
     * @param maxParallelToolCalls 工具调用最大并发数
     */
    public record LlmProperties(
            Integer maxTurns,
            Duration totalTimeout,
            Duration singleCallTimeout,
            Integer observationMaxChars,
            Set<String> enabledTools,
            Integer maxParallelToolCalls) {

        public LlmProperties(Integer maxTurns, Duration totalTimeout,
                Duration singleCallTimeout, Integer observationMaxChars) {
            this(maxTurns, totalTimeout, singleCallTimeout, observationMaxChars,
                    Collections.emptySet(), 4);
        }

        public LlmProperties(Integer maxTurns, Duration totalTimeout,
                Duration singleCallTimeout, Integer observationMaxChars,
                Set<String> enabledTools) {
            this(maxTurns, totalTimeout, singleCallTimeout, observationMaxChars,
                    enabledTools, 4);
        }
    }
}
