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

    public WebClientDpuClient(WebClient dpuWebClient) {
        this.dpuWebClient = dpuWebClient;
    }

    @Override
    public Mono<String> query(DpuRequest request) {
        return dpuWebClient.post()
                .uri("/v1/dpu/query")
                .contentType(MediaType.APPLICATION_JSON)
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
