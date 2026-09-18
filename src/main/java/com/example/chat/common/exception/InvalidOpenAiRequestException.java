package com.example.chat.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * OpenAI Responses 或受信身份请求格式不合法时抛出的异常。
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOpenAiRequestException extends RuntimeException {

    /**
     * 创建请求校验异常。
     *
     * @param message 可安全返回给调用方的错误说明
     */
    public InvalidOpenAiRequestException(String message) {
        super(message);
    }
}