package com.example.chat.integration.client;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 仅从服务端环境读取 Wind 访问凭据的实现。
 */
@Component
public class EnvironmentWindCredentialProvider implements WindCredentialProvider {

    /** Wind API Key 环境变量名称。 */
    private static final String WIND_API_KEY_PROPERTY = "WIND_API_KEY";
    /** Spring 服务端环境。 */
    private final Environment environment;

    /**
     * 创建环境凭据提供方。
     *
     * @param environment Spring 服务端环境
     */
    public EnvironmentWindCredentialProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getApiKey() {
        String apiKey = environment.getProperty(WIND_API_KEY_PROPERTY);
        return StringUtils.hasText(apiKey) ? Optional.of(apiKey.trim()) : Optional.empty();
    }
}
