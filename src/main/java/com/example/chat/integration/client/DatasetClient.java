package com.example.chat.integration.client;

import reactor.core.publisher.Mono;

/**
 * 小作文（数据集）数据源拉取客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface DatasetClient {

    /**
     * 根据问句与 Trace 信息拉取下游指标/小作文的文本内容
     *
     * @param question  改写后的增强问题
     * @param sessionId 当前会话 ID
     * @param userId    当前用户 ID
     * @param taskId    任务追踪 ID
     * @return 小作文内容文本 (DatasetContent)
     */
    Mono<String> fetchDataset(String question, String sessionId, String userId, String taskId);
}
