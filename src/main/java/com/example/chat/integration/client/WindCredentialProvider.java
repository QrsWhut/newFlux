package com.example.chat.integration.client;

import java.util.Optional;

/**
 * Wind 访问凭据提供方。
 */
public interface WindCredentialProvider {

    /**
     * 获取当前进程可用的 Wind API Key。
     *
     * @return 可选 API Key
     */
    Optional<String> getApiKey();
}
