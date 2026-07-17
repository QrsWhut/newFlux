package com.example.chat.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * 响应式 WebClient 统一配置中心
 * 按照每个下游进行连接池的彻底物理隔离，以防止 LLM 长连接阻塞 RAG/DPU 的调用通道
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
public class WebClientConfig {

    private WebClient createIsolatedWebClient(String poolName, DownstreamProperties.ClientProperties props, String defaultAcceptHeader) {
        ConnectionProvider provider = ConnectionProvider.builder(poolName)
                .maxConnections(props.maxConnections())
                .pendingAcquireMaxCount(props.pendingAcquireMaxCount())
                .pendingAcquireTimeout(Duration.ofSeconds(2))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) props.connectTimeout().toMillis())
                .responseTimeout(props.responseTimeout())
                .compress(true);

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(props.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (defaultAcceptHeader != null) {
            builder.defaultHeader(HttpHeaders.ACCEPT, defaultAcceptHeader);
        }

        return builder.build();
    }

    @Bean
    public WebClient llmWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("llm-pool", properties.llm(), MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Bean
    public WebClient ragWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("rag-pool", properties.rag(), MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    public WebClient dpuWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("dpu-pool", properties.dpu(), MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    public WebClient nerWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("ner-pool", properties.ner(), MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    public WebClient viewpointWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("viewpoint-pool", properties.viewpoint(), MediaType.APPLICATION_JSON_VALUE);
    }

    @Bean
    public WebClient rewriteWebClient(DownstreamProperties properties) {
        return createIsolatedWebClient("rewrite-pool", properties.rewrite(), MediaType.APPLICATION_JSON_VALUE);
    }
}
