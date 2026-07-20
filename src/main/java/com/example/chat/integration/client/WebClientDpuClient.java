package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.DpuRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 响应式 WebClient 封装的 DPU 客户端实现类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientDpuClient implements DpuClient {

    private final WebClient dpuWebClient;
    private final com.example.chat.config.DownstreamProperties downstreamProperties;

    public WebClientDpuClient(WebClient dpuWebClient, com.example.chat.config.DownstreamProperties downstreamProperties) {
        this.dpuWebClient = dpuWebClient;
        this.downstreamProperties = downstreamProperties;
    }

    @Override
    public Mono<String> query(DpuRequest request) {
        String baseUrl = downstreamProperties.dpu().baseUrl();
        boolean isMock = baseUrl.contains("localhost:8080");
        String path = isMock ? "/v1/dpu/query" : "/ai/dpu_analysis";

        return dpuWebClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("wind.sessionid", request.sessionId() != null ? request.sessionId() : "")
                .header("no-session", "1")
                .bodyValue(request)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, response -> {
                    log.error("DPU 行情接口 HTTP 状态错误码: {}", response.statusCode());
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("DPU", response.statusCode().value(), type, false, true, "DPU 接口请求异常"));
                })
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("DPU", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "DPU 请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> 
                        new DownstreamException("DPU", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "DPU 接口未知错误", ex));
    }
}
