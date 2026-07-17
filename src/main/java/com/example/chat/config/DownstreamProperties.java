package com.example.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * 下游微服务访问配置属性类
 * 支持不同下游使用独立的连接池限制、超时策略
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@ConfigurationProperties(prefix = "downstream")
public record DownstreamProperties(
        ClientProperties llm,
        ClientProperties rag,
        ClientProperties dpu,
        ClientProperties ner,
        ClientProperties viewpoint,
        ClientProperties rewrite) {

    /**
     * 单个下游服务的详细参数属性
     *
     * @param baseUrl                  基础访问地址
     * @param maxConnections           连接池最大连接数限制
     * @param pendingAcquireMaxCount   排队请求最大限制
     * @param connectTimeout           握手及连接超时时间
     * @param responseTimeout          数据响应读取超时时间
     */
    public record ClientProperties(
            String baseUrl,
            int maxConnections,
            Integer pendingAcquireMaxCount,
            Duration connectTimeout,
            Duration responseTimeout) {

        public Integer pendingAcquireMaxCount() {
            return pendingAcquireMaxCount != null ? pendingAcquireMaxCount : 500;
        }

        public Duration connectTimeout() {
            return connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        }

        public Duration responseTimeout() {
            return responseTimeout != null ? responseTimeout : Duration.ofSeconds(10);
        }
    }
}
