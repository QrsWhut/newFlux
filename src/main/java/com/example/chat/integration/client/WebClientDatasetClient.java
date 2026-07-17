package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 小作文数据集拉取客户端 WebClient 实现
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientDatasetClient implements DatasetClient {

    private final WebClient ragWebClient;

    public WebClientDatasetClient(@Qualifier("ragWebClient") WebClient ragWebClient) {
        this.ragWebClient = ragWebClient;
    }

    @Override
    public Mono<String> fetchDataset(String question, String sessionId, String userId, String taskId) {
        Map<String, Object> req = new HashMap<>();
        req.put("question", question);
        req.put("smartBodyCode", "8056764d7d114a17822a113b559ae277");
        req.put("sessionId", sessionId);
        req.put("userId", userId);
        req.put("traceUuid", taskId);

        log.info("开始拉取下游指标小作文, taskId={}", taskId);

        return ragWebClient.post()
                .uri("/v1/indicators")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("拉取小作文接口 HTTP 状态错误码: {}, taskId={}", response.statusCode(), taskId);
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("DATASET", response.statusCode().value(), type, false, true, "拉取小作文接口异常"));
                })
                .bodyToMono(String.class)
                .map(response -> {
                    if (!StringUtils.hasText(response)) {
                        return "";
                    }
                    try {
                        JSONObject json = JSON.parseObject(response);
                        return json.containsKey("datasetContent") ? json.getString("datasetContent") : "";
                    } catch (com.alibaba.fastjson.JSONException e) {
                        log.error("解析小作文返回 JSON 出现异常, taskId={}, error={}", taskId, e.getMessage());
                        throw new DownstreamException("DATASET", 200, DownstreamException.ErrorType.PARSE_ERROR, false, true, "解析小作文 JSON 异常", e);
                    }
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("DATASET", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "拉取小作文请求超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> 
                        new DownstreamException("DATASET", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "拉取小作文接口未知错误", ex));
    }
}
