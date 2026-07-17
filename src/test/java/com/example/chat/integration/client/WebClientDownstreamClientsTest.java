package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.DpuRequest;
import com.example.chat.common.dto.downstream.RagRequest;
import com.example.chat.common.exception.DownstreamException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 DatasetClient, RagClient, DpuClient 的 MockWebServer 统一网络集成测试。
 * 覆盖 HTTP 正常返回、HTTP 500 报错、网络超时转化为 DownstreamException。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class WebClientDownstreamClientsTest {

    private MockWebServer mockWebServer;
    private WebClientDatasetClient datasetClient;
    private WebClientRagClient ragClient;
    private WebClientDpuClient dpuClient;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        WebClient sharedWebClient = WebClient.builder().baseUrl(baseUrl).build();

        // 实例化三个客户端
        datasetClient = new WebClientDatasetClient(sharedWebClient);
        ragClient = new WebClientRagClient(sharedWebClient);
        dpuClient = new WebClientDpuClient(sharedWebClient);
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // --- 1. DatasetClient 校验 ---

    @Test
    public void testDatasetClient_NormalResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"datasetContent\":\"小作文正文\"}"));

        Mono<String> res = datasetClient.fetchDataset("战略", "sess-1", "user-1", "task-1");

        StepVerifier.create(res)
                .expectNext("小作文正文")
                .verifyComplete();
    }

    @Test
    public void testDatasetClient_Http500Mapping() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        Mono<String> res = datasetClient.fetchDataset("战略", "sess-1", "user-1", "task-1");

        StepVerifier.create(res)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("DATASET", ex.getDownstreamName());
                    assertEquals(500, ex.getHttpStatus());
                    assertEquals(DownstreamException.ErrorType.HTTP_SERVER_ERROR, ex.getErrorType());
                    assertTrue(ex.isDegraded());
                })
                .verify();
    }

    @Test
    public void testDatasetClient_ParseErrorMapping() {
        // 返回不合法 JSON 触发解析错误
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"datasetContent\":"));

        Mono<String> res = datasetClient.fetchDataset("战略", "sess-1", "user-1", "task-1");

        StepVerifier.create(res)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("DATASET", ex.getDownstreamName());
                    assertEquals(DownstreamException.ErrorType.PARSE_ERROR, ex.getErrorType());
                    assertTrue(ex.isDegraded());
                })
                .verify();
    }

    // --- 2. RagClient 校验 ---

    @Test
    public void testRagClient_Http500Mapping() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Error"));

        Mono<String> res = ragClient.retrieve(new RagRequest("问题", "sess-1", 1, 5));

        StepVerifier.create(res)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("RAG", ex.getDownstreamName());
                    assertEquals(500, ex.getHttpStatus());
                    assertEquals(DownstreamException.ErrorType.HTTP_SERVER_ERROR, ex.getErrorType());
                    assertTrue(ex.isDegraded());
                })
                .verify();
    }

    @Test
    public void testRagClient_TimeoutMapping() {
        // 设置延迟响应以触发 10 秒超时
        mockWebServer.enqueue(new MockResponse()
                .setBodyDelay(12, TimeUnit.SECONDS)
                .setBody("RAG"));

        Mono<String> res = ragClient.retrieve(new RagRequest("问题", "sess-1", 1, 5));

        StepVerifier.create(res)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("RAG", ex.getDownstreamName());
                    assertEquals(DownstreamException.ErrorType.RESPONSE_TIMEOUT, ex.getErrorType());
                    assertTrue(ex.isDegraded());
                })
                .verify();
    }

    // --- 3. DpuClient 校验 ---

    @Test
    public void testDpuClient_Http500Mapping() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Error"));

        Mono<String> res = dpuClient.query(new DpuRequest("行情", true));

        StepVerifier.create(res)
                .expectErrorSatisfies(throwable -> {
                    assertTrue(throwable instanceof DownstreamException);
                    DownstreamException ex = (DownstreamException) throwable;
                    assertEquals("DPU", ex.getDownstreamName());
                    assertEquals(500, ex.getHttpStatus());
                    assertEquals(DownstreamException.ErrorType.HTTP_SERVER_ERROR, ex.getErrorType());
                    assertTrue(ex.isDegraded());
                })
                .verify();
    }
}
