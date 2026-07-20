package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
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
 * 响应式 WebClient 封装的命名实体识别 (NER) 客户端实现类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientNerClient implements NerClient {

    private final WebClient nerWebClient;

    public WebClientNerClient(WebClient nerWebClient) {
        this.nerWebClient = nerWebClient;
    }

    @Override
    public Mono<String> extractEntities(String text, String sessionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("outType", "tuple");

        return nerWebClient.post()
                .uri("/v1/ner")
                .contentType(MediaType.APPLICATION_JSON)
                .header("session-id", sessionId)
                .bodyValue(body)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, response -> {
                    log.error("NER 实体识别接口 HTTP 状态错误码: {}", response.statusCode());
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("NER", response.statusCode().value(), type, false, true, "NER 接口请求异常"));
                })
                .bodyToMono(String.class)
                .map(this::parseNer)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("NER", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "NER 请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> {
                    if (ex instanceof java.util.concurrent.CancellationException) {
                        return new DownstreamException("NER", 499, DownstreamException.ErrorType.CANCELLED, false, true, "NER 调用被取消", ex);
                    }
                    return new DownstreamException("NER", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "NER 接口未知错误", ex);
                });
    }

    private String parseNer(String response) {
        if (!StringUtils.hasText(response)) {
            return "[]";
        }
        try {
            JSONObject respJson = JSON.parseObject(response);
            JSONObject windNerPlugInfo = respJson.getJSONObject("windNerPlugInfo");
            if (windNerPlugInfo != null) {
                JSONArray data = windNerPlugInfo.getJSONArray("data");
                if (data != null) {
                    return data.toJSONString();
                }
            }
        } catch (com.alibaba.fastjson.JSONException e) {
            log.error("解析 NER 返回的实体数据异常, 异常: {}", e.getMessage());
            throw new DownstreamException("NER", 200, DownstreamException.ErrorType.PARSE_ERROR, false, true, "解析 NER JSON 异常", e);
        }
        return "[]";
    }
}
