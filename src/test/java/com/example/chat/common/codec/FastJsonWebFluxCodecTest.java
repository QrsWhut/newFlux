package com.example.chat.common.codec;

import com.alibaba.fastjson.JSON;
import com.example.chat.config.FastJsonWebFluxConfiguration;
import com.example.chat.web.vo.ChatRequestVO;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FastJSON WebFlux 编解码测试。
 */
class FastJsonWebFluxCodecTest {

    /** 正常测试最大字节数。 */
    private static final int NORMAL_MAX_BYTES = 1_024;
    /** 限流测试最大字节数。 */
    private static final int SMALL_MAX_BYTES = 16;

    /**
     * 验证 JSON 请求通过 FastJSON 解码并释放输入缓冲区。
     */
    @Test
    void shouldDecodeJsonAndReleaseInputBuffer() {
        FastJsonDecoder decoder = new FastJsonDecoder(NORMAL_MAX_BYTES);
        NettyDataBufferFactory bufferFactory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        NettyDataBuffer inputBuffer = (NettyDataBuffer) bufferFactory.wrap(
                "{\"sessionId\":\"session-001\",\"question\":\"问题\"}"
                        .getBytes(StandardCharsets.UTF_8));

        Mono<Object> decoded = decoder.decodeToMono(
                Mono.just(inputBuffer),
                ResolvableType.forClass(ChatRequestVO.class),
                MediaType.APPLICATION_JSON,
                Map.of());

        StepVerifier.create(decoded)
                .assertNext(value -> {
                    ChatRequestVO requestVO = assertInstanceOf(
                            ChatRequestVO.class,
                            value);
                    assertEquals("session-001", requestVO.getSessionId());
                    assertEquals("问题", requestVO.getQuestion());
                })
                .verifyComplete();
        assertEquals(0, inputBuffer.getNativeBuffer().refCnt());
    }

    /**
     * 验证请求体超过限制时失败且释放输入缓冲区。
     */
    @Test
    void shouldRejectOversizedJsonAndReleaseInputBuffer() {
        FastJsonDecoder decoder = new FastJsonDecoder(SMALL_MAX_BYTES);
        NettyDataBufferFactory bufferFactory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        NettyDataBuffer inputBuffer = (NettyDataBuffer) bufferFactory.wrap(
                "{\"question\":\"这是一个超过限制的请求\"}"
                        .getBytes(StandardCharsets.UTF_8));

        Mono<Object> decoded = decoder.decodeToMono(
                Mono.just(inputBuffer),
                ResolvableType.forClass(ChatRequestVO.class),
                MediaType.APPLICATION_JSON,
                Map.of());

        StepVerifier.create(decoded)
                .expectError(DataBufferLimitException.class)
                .verify();
        assertEquals(0, inputBuffer.getNativeBuffer().refCnt());
    }

    /**
     * 验证非法 JSON 被转换为明确的解码异常。
     */
    @Test
    void shouldRejectMalformedJson() {
        FastJsonDecoder decoder = new FastJsonDecoder(NORMAL_MAX_BYTES);
        NettyDataBufferFactory bufferFactory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        NettyDataBuffer inputBuffer = (NettyDataBuffer) bufferFactory.wrap(
                "{invalid}".getBytes(StandardCharsets.UTF_8));

        Mono<Object> decoded = decoder.decodeToMono(
                Mono.just(inputBuffer),
                ResolvableType.forClass(ChatRequestVO.class),
                MediaType.APPLICATION_JSON,
                Map.of());

        StepVerifier.create(decoded)
                .expectError(DecodingException.class)
                .verify();
        assertEquals(0, inputBuffer.getNativeBuffer().refCnt());
    }

    /**
     * 验证 JSON 响应通过 FastJSON 编码并受字节数限制。
     */
    @Test
    void shouldEncodeJsonAndEnforceMaximumBytes() {
        ChatRequestVO requestVO = new ChatRequestVO();
        requestVO.setSessionId("session-001");
        requestVO.setQuestion("问题");
        NettyDataBufferFactory bufferFactory =
                new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT);
        FastJsonEncoder encoder = new FastJsonEncoder(NORMAL_MAX_BYTES);

        DataBuffer encoded = encoder.encodeValue(
                requestVO,
                bufferFactory,
                ResolvableType.forClass(ChatRequestVO.class),
                MediaType.APPLICATION_JSON,
                Map.of());
        byte[] bytes = new byte[encoded.readableByteCount()];
        encoded.read(bytes);
        DataBufferUtils.release(encoded);

        ChatRequestVO decoded = JSON.parseObject(bytes, ChatRequestVO.class);
        assertEquals("session-001", decoded.getSessionId());
        assertThrows(
                EncodingException.class,
                () -> new FastJsonEncoder(SMALL_MAX_BYTES).encodeValue(
                        requestVO,
                        bufferFactory,
                        ResolvableType.forClass(ChatRequestVO.class),
                        MediaType.APPLICATION_JSON,
                        Map.of()));
    }

    /**
     * 验证 FastJSON 读写器优先于默认 JSON codec，且 SSE 使用 FastJSON 数据编码器。
     */
    @Test
    void shouldRegisterFastJsonBeforeDefaultsAndKeepSseWriter() {
        ServerCodecConfigurer codecConfigurer = ServerCodecConfigurer.create();
        new FastJsonWebFluxConfiguration(NORMAL_MAX_BYTES)
                .configureHttpMessageCodecs(codecConfigurer);
        ResolvableType bodyType = ResolvableType.forClass(ChatRequestVO.class);

        HttpMessageReader<?> jsonReader = codecConfigurer.getReaders().stream()
                .filter(reader -> reader.canRead(bodyType, MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();
        DecoderHttpMessageReader<?> decoderReader = assertInstanceOf(
                DecoderHttpMessageReader.class,
                jsonReader);
        Decoder<?> decoder = decoderReader.getDecoder();
        assertInstanceOf(FastJsonDecoder.class, decoder);

        HttpMessageWriter<?> jsonWriter = codecConfigurer.getWriters().stream()
                .filter(writer -> writer.canWrite(bodyType, MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();
        EncoderHttpMessageWriter<?> encoderWriter = assertInstanceOf(
                EncoderHttpMessageWriter.class,
                jsonWriter);
        Encoder<?> encoder = encoderWriter.getEncoder();
        assertInstanceOf(FastJsonEncoder.class, encoder);

        HttpMessageWriter<?> sseWriter = codecConfigurer.getWriters().stream()
                .filter(writer -> writer.canWrite(
                        ResolvableType.forClass(ServerSentEvent.class),
                        MediaType.TEXT_EVENT_STREAM))
                .findFirst()
                .orElseThrow();
        ServerSentEventHttpMessageWriter eventWriter = assertInstanceOf(
                ServerSentEventHttpMessageWriter.class,
                sseWriter);
        assertTrue(eventWriter.getEncoder() instanceof FastJsonEncoder);
    }
}