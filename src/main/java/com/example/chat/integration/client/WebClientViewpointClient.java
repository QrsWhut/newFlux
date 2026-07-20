package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
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
 * 响应式 WebClient 封装的观点校验客户端实现类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientViewpointClient implements ViewpointClient {

    private final WebClient viewpointWebClient;
    private final com.example.chat.config.DownstreamProperties downstreamProperties;

    public WebClientViewpointClient(WebClient viewpointWebClient, com.example.chat.config.DownstreamProperties downstreamProperties) {
        this.viewpointWebClient = viewpointWebClient;
        this.downstreamProperties = downstreamProperties;
    }

    @Override
    public Mono<String> checkViewpoint(String question, String answer, String sessionId, String userId) {
        String baseUrl = downstreamProperties.viewpoint().baseUrl();
        boolean isMock = baseUrl.contains("localhost:8080");
        String path = isMock ? "/v1/viewpoint" : "/ai/run_workflow";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("userQuery", question);
        inputs.put("ab1Reply", answer);

        Map<String, Object> body = new HashMap<>();
        body.put("strAppCode", "94b0df59-e3a8-4971-899e-6917d0f167f3");
        body.put("jsonParamObject", inputs);
        body.put("sessionid", sessionId);
        body.put("userid", userId);
        body.put("responseMode", "blocking");

        return viewpointWebClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("wind.sessionid", sessionId)
                .bodyValue(body)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, response -> {
                    log.error("观点校验接口 HTTP 状态错误码: {}", response.statusCode());
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("VIEWPOINT", response.statusCode().value(), type, false, true, "观点校验接口请求异常"));
                })
                .bodyToMono(String.class)
                .map(this::parseViewpoint)
                .timeout(Duration.ofSeconds(30))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("VIEWPOINT", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "观点校验请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> {
                    if (ex instanceof java.util.concurrent.CancellationException) {
                        return new DownstreamException("VIEWPOINT", 499, DownstreamException.ErrorType.CANCELLED, false, true, "观点校验被取消", ex);
                    }
                    return new DownstreamException("VIEWPOINT", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "观点校验接口未知错误", ex);
                });
    }

    private String parseViewpoint(String response) {
        if (!StringUtils.hasText(response)) {
            return "0";
        }
        try {
            String jsonStr = response;
            if (jsonStr.startsWith("data:")) {
                jsonStr = jsonStr.substring(5).trim();
            }
            JSONObject json = JSON.parseObject(jsonStr);
            
            // 优先尝试从 data.outputs.result 获取
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                JSONObject outputs = data.getJSONObject("outputs");
                if (outputs != null) {
                    String result = outputs.getString("result");
                    if (result != null) {
                        return "True".equalsIgnoreCase(result.trim()) ? "1" : "0";
                    }
                }
            }
            
            // 兜底从 outputs.result 获取
            JSONObject outputs = json.getJSONObject("outputs");
            if (outputs != null) {
                String result = outputs.getString("result");
                if (result != null) {
                    return "True".equalsIgnoreCase(result.trim()) ? "1" : "0";
                }
            }
        } catch (com.alibaba.fastjson.JSONException e) {
            log.error("解析观点可生成校验接口 JSON 响应异常。原始数据: {}, 异常: {}", response, e.getMessage());
            throw new DownstreamException("VIEWPOINT", 200, DownstreamException.ErrorType.PARSE_ERROR, false, true, "解析观点校验 JSON 异常", e);
        }
        return "0";
    }
}
