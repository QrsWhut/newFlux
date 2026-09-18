package com.example.chat.common.security;

/**
 * 由受信身份边界解析出的请求身份。
 *
 * @param userId 已认证的用户标识
 */
public record RequestIdentity(String userId) {
}
