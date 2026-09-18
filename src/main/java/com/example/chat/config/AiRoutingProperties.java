package com.example.chat.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模型供应商路由配置。
 *
 * @author Codex
 * @since 2026-08-25
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "ai.routing")
public class AiRoutingProperties {

    /** 默认供应商标识。 */
    private String defaultProvider;

    /** 按供应商标识组织的模型访问配置。 */
    private Map<String, ProviderProperties> providers;

    /**
     * 获取不可变的供应商配置视图。
     *
     * @return 供应商配置；未配置时返回空集合
     */
    public Map<String, ProviderProperties> providerView() {
        if (providers == null || providers.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    /**
     * 单个 AI 模型供应商配置。
     *
     * @author Codex
     * @since 2026-08-25
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderProperties {

        /** 供应商 API 基础地址。 */
        private String baseUrl;

        /** 供应商 API 密钥。 */
        private String apiKey;

        /** 默认模型标识。 */
        private String model;

        /** Responses API 请求路径。 */
        private String responsesPath;

        /** 独立连接池最大连接数。 */
        private Integer maxConnections;

        /** 连接池最大等待请求数。 */
        private Integer pendingAcquireMaxCount;

        /** 建立连接超时时间。 */
        private Duration connectTimeout;

        /** 流式响应超时时间。 */
        private Duration responseTimeout;
    }
}
