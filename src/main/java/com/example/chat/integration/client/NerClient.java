package com.example.chat.integration.client;

import reactor.core.publisher.Mono;

/**
 * 金融命名实体识别 (NER) 客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface NerClient {

    /**
     * 对大模型回答文本或者用户问句进行命名实体识别，提取其中的股票、基金、指标等金融实体
     *
     * @param text      待识别内容
     * @param sessionId 当前会话 ID
     * @return 命名实体列表的 JSON 数组字符串
     */
    Mono<String> extractEntities(String text, String sessionId);
}
