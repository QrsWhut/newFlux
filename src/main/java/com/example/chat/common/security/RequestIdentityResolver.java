package com.example.chat.common.security;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 请求身份解析接口。
 */
public interface RequestIdentityResolver {

    /**
     * 从受信请求边界解析身份，不读取业务请求体中的用户标识。
     *
     * @param request 服务端请求
     * @return 已认证请求身份
     */
    RequestIdentity resolve(ServerHttpRequest request);
}
