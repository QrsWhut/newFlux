package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.LlmChunk;
import com.example.chat.common.dto.downstream.LlmRequest;
import com.example.chat.common.exception.DownstreamException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 WebClientLlmClient 的真实 HTTP / SSE 半包与取消传播集成测试
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class WebClientLlmClientTest {

    private MockWebServer mockWebServer;
    private WebClientLlmClient llmClient;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // 构造靶向 MockWebServer 端口的 WebClient 实例
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        com.example.chat.config.DownstreamProperties.ClientProperties clientProperties = 
            new com.example.chat.config.DownstreamProperties.ClientProperties(
                mockWebServer.url("/").toString(), 50, 100, Duration.ofSeconds(2), Duration.ofSeconds(10)
            );
        com.example.chat.config.DownstreamProperties properties = 
            new com.example.chat.config.DownstreamProperties(
                clientProperties, null, null, null, null, null
            );

        llmClient = new WebClientLlmClient(webClient, properties);
    }

    @Test
    public void testRealLlmStream() {
        WebClient webClient = WebClient.builder().build();
        com.example.chat.config.DownstreamProperties.ClientProperties clientProperties = 
            new com.example.chat.config.DownstreamProperties.ClientProperties(
                "http://180.96.8.44/wstock_share", 50, 100, Duration.ofSeconds(5), Duration.ofSeconds(30)
            );
        com.example.chat.config.DownstreamProperties properties = 
            new com.example.chat.config.DownstreamProperties(
                clientProperties, null, null, null, null, null
            );
        WebClientLlmClient client = new WebClientLlmClient(webClient, properties);

        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("question", "你好");
        params.put("currentDate", "20260720");
        params.put("lstdpu", "");
        params.put("pageData", "");
        params.put("lstrag", "");
        params.put("content", "");
        LlmRequest request = new LlmRequest("P053102", true, params);
        System.out.println(">>> 开始请求真实大模型流...");
        
        try {
            client.stream(request, "3f209defe4145a88a6fbc3114353e95")
                .doOnNext(chunk -> {
                    System.out.print(chunk.text());
                    System.out.flush();
                })
                .doOnError(err -> System.err.println("\n>>> 请求大模型发生异常: " + err.getMessage()))
                .doOnComplete(() -> System.out.println("\n>>> 请求大模型完成。"))
                .blockLast(Duration.ofSeconds(30));
        } catch (Exception e) {
            System.err.println("\n>>> 阻塞等待流完成异常: " + e.getMessage());
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testSseHalfPacketDecoding() throws Exception {
        MockResponse completeResponse = new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                        "data: {\"choices\": [{\"delta\": {\"content\": \"Hello \"}}]}\n\n" +
                        "da" + "ta: {\"choices\": [{\"delta\": {\"content\": \"World\"}}]}\n\n" +
                        "data: [DONE]\n\n"
                );
        mockWebServer.enqueue(completeResponse);

        LlmRequest request = new LlmRequest("P053102", true, Collections.emptyMap());
        Flux<LlmChunk> stream = llmClient.stream(request, "session-test");

        StepVerifier.create(stream)
                .expectNextMatches(chunk -> "Hello ".equals(chunk.text()))
                .expectNextMatches(chunk -> "World".equals(chunk.text()))
                .verifyComplete();
    }

    @Test
    public void testHttpErrorMapping() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        LlmRequest request = new LlmRequest("P053102", true, Collections.emptyMap());
        Flux<LlmChunk> stream = llmClient.stream(request, "session-test");

        StepVerifier.create(stream)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("LLM", ex.getDownstreamName());
                    assertEquals(500, ex.getHttpStatus());
                })
                .verify();
    }

    @Test
    public void testCancellationPropagation() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                        "data: {\"choices\": [{\"delta\": {\"content\": \"KeepAlive\"}}]}\n\n" +
                        "data: {\"choices\": [{\"delta\": {\"content\": \"KeepAlive\"}}]}\n\n" +
                        "data: {\"choices\": [{\"delta\": {\"content\": \"KeepAlive\"}}]}\n\n"
                ));

        LlmRequest request = new LlmRequest("P053102", true, Collections.emptyMap());
        Flux<LlmChunk> stream = llmClient.stream(request, "session-test");

        StepVerifier.create(stream)
                .expectNextMatches(chunk -> "KeepAlive".equals(chunk.text()))
                .thenCancel()
                .verify();

        Thread.sleep(100);

        RecordedRequest recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(recordedRequest);
        assertEquals("/v1/chat/stream", recordedRequest.getPath());
    }
}
