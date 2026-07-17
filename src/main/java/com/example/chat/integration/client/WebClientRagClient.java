package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.downstream.RagRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 响应式 WebClient 封装的 RAG 客户端实现类
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientRagClient implements RagClient {

    private final WebClient ragWebClient;

    public WebClientRagClient(WebClient ragWebClient) {
        this.ragWebClient = ragWebClient;
    }

    @Override
    public Mono<String> retrieve(RagRequest request) {
        return ragWebClient.post()
                .uri("/v1/rag/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, response -> {
                    log.error("RAG检索接口 HTTP 状态错误码: {}", response.statusCode());
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("RAG", response.statusCode().value(), type, false, true, "RAG 检索接口异常"));
                })
                .bodyToMono(String.class)
                .map(this::extractRagData)
                .timeout(Duration.ofSeconds(10))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("RAG", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, true, "RAG 请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> 
                        new DownstreamException("RAG", 500, DownstreamException.ErrorType.UNKNOWN, false, true, "RAG 接口未知错误", ex));
    }

    private String extractRagData(String response) {
        if (!StringUtils.hasText(response)) {
            return "";
        }
        try {
            JSONObject ragJson = JSON.parseObject(response);
            String resultStr = ragJson.getString("result");
            if (!StringUtils.hasText(resultStr)) {
                return "";
            }
            JSONArray resultArray = JSON.parseArray(resultStr);
            JSONArray filteredArray = new JSONArray();
            if (resultArray != null) {
                for (int i = 0; i < resultArray.size(); i++) {
                    JSONObject item = resultArray.getJSONObject(i);
                    JSONObject filteredItem = new JSONObject();
                    filteredItem.put("title", item.getString("title"));
                    filteredItem.put("content", item.getString("content"));
                    filteredItem.put("publish_date", item.getString("publish_date"));
                    filteredArray.add(filteredItem);
                }
            }
            return filteredArray.toJSONString();
        } catch (Exception e) {
            log.warn("解析 RAG 原始响应 JSON 失败，原始响应={}, 错误: {}", response, e.getMessage());
            return "";
        }
    }
}
