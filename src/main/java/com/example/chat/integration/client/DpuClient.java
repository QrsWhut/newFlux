package com.example.chat.integration.client;

import com.example.chat.common.dto.downstream.DpuRequest;
import reactor.core.publisher.Mono;

/**
 * DPU 行情指标检索客户端接口
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public interface DpuClient {

    /**
     * 调用 DPU 查询服务拉取金融指标并进行结构化清洗，返回最终的分析背景数据
     *
     * @param request DPU 请求参数
     * @return DPU 分析计算后的背景文本
     */
    Mono<String> query(DpuRequest request);
}
