package com.example.chat.common.codec;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.serializer.SerializeConfig;
import org.springframework.boot.actuate.health.Status;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 基于 FastJSON 的 WebFlux JSON 编码器。
 */
public class FastJsonEncoder extends AbstractEncoder<Object> {

    /** 响应对象最大序列化字节数。 */
    private final int maxBytes;
    /** FastJSON 序列化配置。 */
    private final SerializeConfig serializeConfig;

    /**
     * 创建 JSON 编码器。
     *
     * @param maxBytes 单个响应对象最大字节数
     */
    public FastJsonEncoder(int maxBytes) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("响应对象最大字节数必须大于零");
        }
        this.maxBytes = maxBytes;
        this.serializeConfig = new SerializeConfig();
        this.serializeConfig.put(Status.class, (serializer, object,
                fieldName, fieldType, features) -> serializer.write(
                ((Status) object).getCode()));
    }

    /**
     * 编码响应对象流。
     *
     * @param inputStream 响应对象流
     * @param bufferFactory 缓冲区工厂
     * @param elementType 元素类型
     * @param mimeType 媒体类型
     * @param hints 编码提示
     * @return 编码后的缓冲区流
     */
    @Override
    public Flux<DataBuffer> encode(
            Publisher<? extends Object> inputStream,
            DataBufferFactory bufferFactory,
            ResolvableType elementType,
            MimeType mimeType,
            Map<String, Object> hints) {
        return Flux.from(inputStream)
                .map(value -> encodeValue(
                        value,
                        bufferFactory,
                        elementType,
                        mimeType,
                        hints));
    }

    /**
     * 编码单个响应对象。
     *
     * @param value 响应对象
     * @param bufferFactory 缓冲区工厂
     * @param valueType 对象类型
     * @param mimeType 媒体类型
     * @param hints 编码提示
     * @return 编码后的缓冲区
     */
    @Override
    public DataBuffer encodeValue(
            Object value,
            DataBufferFactory bufferFactory,
            ResolvableType valueType,
            MimeType mimeType,
            Map<String, Object> hints) {
        byte[] bytes;
        try {
            bytes = JSON.toJSONBytes(value, serializeConfig);
        } catch (JSONException exception) {
            throw new EncodingException("JSON 响应序列化失败", exception);
        }
        if (bytes.length > maxBytes) {
            throw new EncodingException("JSON 响应超过最大字节数限制");
        }
        return bufferFactory.wrap(bytes);
    }
}