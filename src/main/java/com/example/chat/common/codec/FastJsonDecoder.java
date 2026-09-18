package com.example.chat.common.codec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 FastJSON 的 WebFlux JSON 解码器。
 */
public class FastJsonDecoder extends AbstractDataBufferDecoder<Object> {

    /** 请求体最大字节数。 */
    private final int maxBytes;

    /**
     * 创建 JSON 解码器。
     *
     * @param maxBytes 请求体最大字节数
     */
    public FastJsonDecoder(int maxBytes) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("请求体最大字节数必须大于零");
        }
        this.maxBytes = maxBytes;
    }

    /**
     * JSON 必须聚合后才能安全解析，禁止按分片解码。
     *
     * @param buffer 输入缓冲区
     * @param elementType 目标类型
     * @param mimeType 媒体类型
     * @param hints 解码提示
     * @return 不支持分片解码
     */
    @Override
    public Object decode(
            DataBuffer buffer,
            ResolvableType elementType,
            MimeType mimeType,
            Map<String, Object> hints) {
        throw new UnsupportedOperationException("JSON 请求必须使用聚合解码");
    }

    /**
     * 聚合请求体并使用 FastJSON 解码。
     *
     * @param input 请求体分片
     * @param elementType 目标类型
     * @param mimeType 媒体类型
     * @param hints 解码提示
     * @return 解码结果
     */
    @Override
    public Mono<Object> decodeToMono(
            Publisher<DataBuffer> input,
            ResolvableType elementType,
            MimeType mimeType,
            Map<String, Object> hints) {
        return DataBufferUtils.join(input, maxBytes)
                .map(buffer -> decodeAndRelease(buffer, elementType));
    }

    /**
     * 解码对象流。
     *
     * @param input 请求体分片
     * @param elementType 目标类型
     * @param mimeType 媒体类型
     * @param hints 解码提示
     * @return 单元素结果流
     */
    @Override
    public Flux<Object> decode(
            Publisher<DataBuffer> input,
            ResolvableType elementType,
            MimeType mimeType,
            Map<String, Object> hints) {
        return decodeToMono(input, elementType, mimeType, hints).flux();
    }

    private Object decodeAndRelease(DataBuffer buffer, ResolvableType elementType) {
        try {
            if (buffer.readableByteCount() > maxBytes) {
                throw new DataBufferLimitException("JSON 请求体超过最大字节数限制" );
            }
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return JSON.parseObject(
                    new String(bytes, StandardCharsets.UTF_8),
                    elementType.getType());
        } catch (JSONException exception) {
            throw new DecodingException("JSON 请求体格式非法", exception);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}