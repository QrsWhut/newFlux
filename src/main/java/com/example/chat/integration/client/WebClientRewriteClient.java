package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.downstream.MultiRewriteParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 响应式 WebClient 封装的改写服务客户端实现类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientRewriteClient implements RewriteClient {

    private final WebClient rewriteWebClient;

    public WebClientRewriteClient(WebClient rewriteWebClient) {
        this.rewriteWebClient = rewriteWebClient;
    }

    @Override
    public Mono<String> rewrite(MultiRewriteParam param) {
        return rewriteWebClient.post()
                .uri("/v1/rewrite")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(param)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, response -> {
                    log.error("问句改写接口 HTTP 状态错误码: {}", response.statusCode());
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("REWRITE", response.statusCode().value(), type, false, true, "改写接口请求异常"));
                })
                .bodyToMono(String.class)
                .map(this::parseRewrite)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("REWRITE", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "问句改写请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> {
                    if (ex instanceof java.util.concurrent.CancellationException) {
                        return new DownstreamException("REWRITE", 499, DownstreamException.ErrorType.CANCELLED, false, true, "改写调用被取消", ex);
                    }
                    return new DownstreamException("REWRITE", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "问句改写接口未知错误", ex);
                });
    }

    private String parseRewrite(String response) {
        if (!StringUtils.hasText(response)) {
            return "";
        }
        try {
            JSONObject respJson = JSON.parseObject(response);
            JSONObject body = respJson.getJSONObject("body");
            if (body != null) {
                JSONArray choices = body.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");
                    if (message != null) {
                        String contentStr = message.getString("content");
                        if (StringUtils.hasText(contentStr)) {
                            JSONObject contentJson = JSON.parseObject(contentStr);
                            return contentJson.getString("rewrite_sentence");
                        }
                    }
                }
            }
        } catch (com.alibaba.fastjson.JSONException e) {
            log.error("解析改写接口返回 JSON 异常, 原始响应数据={}, 异常: {}", response, e.getMessage());
            throw new DownstreamException("REWRITE", 200, DownstreamException.ErrorType.PARSE_ERROR, false, true, "解析改写 JSON 异常", e);
        }
        return "";
    }
}
