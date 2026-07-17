package com.example.chat.common.dto;

/**
 * 封装 RAG 与 DPU 行情并行富化检索结果的不可变 DTO
 *
 * @param ragData  RAG 检索的 JSON 字符串
 * @param dpuData  DPU 行情接口的 JSON 字符串
 * @author Antigravity
 * @since 2026-07-17
 */
public record EnrichmentResult(String ragData, String dpuData) {
}
