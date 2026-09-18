package com.example.chat.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 受信身份边界缺少安全配置时抛出的异常。
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class IdentityConfigurationException extends RuntimeException {

    /**
     * 创建身份边界配置异常。
     *
     * @param message 服务端配置错误说明
     */
    public IdentityConfigurationException(String message) {
        super(message);
    }
}