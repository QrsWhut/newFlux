package com.example.chat.config;

import com.example.chat.common.codec.FastJsonDecoder;
import com.example.chat.common.codec.FastJsonEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.ServerSentEventHttpMessageWriter;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux FastJSON 编解码配置。
 */
@Configuration
public class FastJsonWebFluxConfiguration implements WebFluxConfigurer {

    /** JSON 单对象最大字节数。 */
    private final int maxBytes;

    /**
     * 创建编解码配置。
     *
     * @param maxBytes JSON 单对象最大字节数
     */
    public FastJsonWebFluxConfiguration(
            @Value("${app.web.max-json-bytes:1048576}") int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * 将 FastJSON 注册为 JSON 与 SSE 数据的首选编解码器。
     *
     * @param configurer 服务端编解码配置
     */
    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        FastJsonDecoder decoder = new FastJsonDecoder(maxBytes);
        FastJsonEncoder encoder = new FastJsonEncoder(maxBytes);
        configurer.customCodecs().registerWithDefaultConfig(decoder);
        configurer.customCodecs().registerWithDefaultConfig(encoder);
        configurer.customCodecs().register(
                new ServerSentEventHttpMessageWriter(encoder));
    }
}
