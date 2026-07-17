package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.MultiRewriteParam;
import reactor.core.publisher.Mono;

/**
 * 多轮历史对话问句改写客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface RewriteClient {

    /**
     * 调用下游改写服务，将多轮对话问句改写为适用于检索的增强问句
     *
     * @param param 改写参数，包括历史消息等
     * @return 改写后的问题内容
     */
    Mono<String> rewrite(MultiRewriteParam param);
}
