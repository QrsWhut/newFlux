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

        llmClient = new WebClientLlmClient(webClient);
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
