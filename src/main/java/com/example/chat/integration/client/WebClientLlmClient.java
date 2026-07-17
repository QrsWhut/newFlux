package com.example.chat.integration.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.chat.common.dto.downstream.LlmChunk;
import com.example.chat.common.dto.downstream.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.chat.common.exception.DownstreamException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 响应式 WebClient 封装的大模型流式客户端实现类
 * 使用标准的 ServerSentEvent 流式处理器解析 SSE 协议，解决粘包半包与控制头杂音问题
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class WebClientLlmClient implements LlmClient {

    private final WebClient llmWebClient;

    public WebClientLlmClient(WebClient llmWebClient) {
        this.llmWebClient = llmWebClient;
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request, String sessionId) {
        ParameterizedTypeReference<ServerSentEvent<String>> typeRef = 
                new ParameterizedTypeReference<>() {};

        return llmWebClient.post()
                .uri("/v1/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .header("session-id", sessionId)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("大模型流请求接口 HTTP 状态错误码: {}, sessionId={}", response.statusCode(), sessionId);
                    boolean isClient = response.statusCode().is4xxClientError();
                    DownstreamException.ErrorType type = isClient ? DownstreamException.ErrorType.HTTP_CLIENT_ERROR : DownstreamException.ErrorType.HTTP_SERVER_ERROR;
                    return Mono.error(new DownstreamException("LLM", response.statusCode().value(), type, false, false, "大模型流式接口异常"));
                })
                .bodyToFlux(typeRef)
                .takeWhile(sse -> sse.data() != null && !"[DONE]".equals(sse.data().trim()))
                .map(sse -> parseChunk(sse.data()))
                .filter(chunk -> chunk != null && !chunk.text().isEmpty())
                .timeout(Duration.ofSeconds(60))
                .onErrorMap(java.util.concurrent.TimeoutException.class, ex -> 
                        new DownstreamException("LLM", 504, DownstreamException.ErrorType.RESPONSE_TIMEOUT, true, false, "大模型流请求响应超时", ex))
                .onErrorMap(ex -> !(ex instanceof DownstreamException), ex -> 
                        new DownstreamException("LLM", 500, DownstreamException.ErrorType.UNKNOWN, false, false, "大模型流接口未知错误", ex))
                .doOnCancel(() -> log.info("大模型连接被客户端主动断开/取消，sessionId={}", sessionId));
    }

    private LlmChunk parseChunk(String data) {
        if (data == null || data.isEmpty()) {
            return new LlmChunk("", "");
        }
        try {
            String jsonStr = data.trim();
            if (jsonStr.isEmpty() || "[DONE]".equals(jsonStr)) {
                return new LlmChunk("", "");
            }
            JSONObject json = JSON.parseObject(jsonStr);
            JSONArray choices = json.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject delta = firstChoice.getJSONObject("delta");
                if (delta != null) {
                    String content = delta.containsKey("content") ? delta.getString("content") : "";
                    String reasoningContent = delta.containsKey("reasoning_content") ? delta.getString("reasoning_content") : "";
                    return new LlmChunk(content, reasoningContent);
                }
            }
        } catch (Exception e) {
            log.warn("解析大模型流切片 JSON 失败, data={}, error={}", data, e.getMessage());
        }
        return new LlmChunk("", "");
    }
}
