package com.example.chat.integration.client;

import reactor.core.publisher.Mono;

/**
 * 观点可生成校验服务客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface ViewpointClient {

    /**
     * 判断当前对话问题及大模型回答内容是否支持生成对应的行情/财经观点
     *
     * @param question  用户的增强问句
     * @param answer    首段+次段的大模型回答内容
     * @param sessionId 用户会话 ID
     * @param userId    用户唯一 ID
     * @return "1" 代表可生成，"0" 代表不可生成
     */
    Mono<String> checkViewpoint(String question, String answer, String sessionId, String userId);
}
