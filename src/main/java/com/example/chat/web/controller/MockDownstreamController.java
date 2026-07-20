package com.example.chat.web.controller;

import com.alibaba.fastjson.JSON;
import com.example.chat.common.dto.downstream.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 调试专用：内置的下游微服务 Mock 接口控制器
 * 将下游 base-url 均配回 localhost:8080 后即可在无 8081 仿真服务的前提下自给自足地跑通完整流式对话。
 *
 * @author Antigravity
 * @since 2026-07-20
 */
@Slf4j
@RestController
@RequestMapping("/v1")
public class MockDownstreamController {

    /**
     * 1. 问句改写 Mock
     */
    @PostMapping("/rewrite")
    public Mono<?> rewrite(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: 问句改写收到请求: {}", req);
        String question = (String) req.get("question");
        String rewritten = "关于 " + (question != null ? question : "") + " 的专业金融投资分析";
        
        Map<String, Object> response = Map.of(
            "body", Map.of(
                "choices", List.of(
                    Map.of("message", Map.of("content", JSON.toJSONString(Map.of("rewrite_sentence", rewritten))))
                )
            )
        );
        return Mono.just(response);
    }

    /**
     * 2. 小作文拉取 Mock
     */
    @PostMapping("/indicators")
    public Mono<?> fetchDataset(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: 拉取小作文收到请求: {}", req);
        String question = (String) req.get("question");
        String content = "【仿真指标分析报告】针对用户关于“" + question + "”的财务诊断：当前成长因子分数为 88 分，资金面表现出稳步的流入态势，毛利率表现领先行业均值。";
        return Mono.just(Map.of("datasetContent", content))
                .delayElement(Duration.ofMillis(300)); // 延迟 300ms 模拟网络
    }

    /**
     * 3. RAG 检索 Mock
     */
    @PostMapping("/rag/retrieve")
    public Mono<?> queryRag(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: RAG 检索收到请求: {}", req);
        String rewritten = "贵州茅台在高端白酒市场的统治力依然强劲，近三年净利润复合增长率达16.2%。渠道改革效果显著，直销比例突破45%，品牌护城河极深。";
        String resultStr = JSON.toJSONString(List.of(
            Map.of("title", "贵州茅台基本面深度报告", "content", rewritten, "publish_date", "2026-07-20")
        ));
        return Mono.just(Map.of("result", resultStr))
                .delayElement(Duration.ofMillis(400));
    }

    /**
     * 4. DPU 行情 Mock
     */
    @PostMapping("/dpu/query")
    public Mono<String> queryDpu(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: DPU 收到请求: {}", req);
        String question = (String) req.get("question");
        String dpuText = "DPU_MARKET: 对应行情标的当前交易额 12.8 亿，主力净买入 4200 万，买卖气势比 1.15，多方略微占优。";
        return Mono.just(dpuText).delayElement(Duration.ofMillis(350));
    }

    /**
     * 5. LLM 流式输出 Mock
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamLlm(@RequestBody LlmRequest req) {
        log.info("Mock 下游: 大模型流式请求收到, promptId={}", req.promptId());
        
        // 5.1 追问模型 Mock
        if ("P052186".equals(req.promptId())) {
            List<String> list = List.of(
                "近三年直销渠道的毛利率对比如何？",
                "茅台账面现金充足，其分红率未来有何预测？",
                "如何评估白酒行业去库存周期对茅台的影响？"
            );
            String jsonStr = JSON.toJSONString(list);
            return Flux.just(ServerSentEvent.builder(buildLlmChunkJson(jsonStr, "")).build());
        }

        // 5.2 常规模型回答 Mock (流式吐字)
        String[] tokens;
        if ("P053102".equals(req.promptId())) {
            tokens = new String[]{
                "根据您", "提问的", "基本面", "数据，分析", "结果如下：\n",
                "1. 公司", "在过去三个", "财年的营业", "收入持续", "稳步上升；\n",
                "2. 扣非", "净利润增速", "保持在双位数", "区间，符合", "蓝筹核心", "资产定位。\n",
                "#end# 阿里"
            };
        } else {
            tokens = new String[]{
                "这是第", "二轮深入", "诊断分析：\n",
                "结合最新行情", "主力资金", "动向显示，该标的", "在年线上方", "整固充分，", "可积极关注", "量能释放。"
            };
        }

        return Flux.interval(Duration.ofMillis(100))
                .take(tokens.length)
                .map(index -> {
                    String token = tokens[index.intValue()];
                    String data = buildLlmChunkJson(token, "");
                    return ServerSentEvent.builder(data).build();
                });
    }

    /**
     * 6. NER 实体识别 Mock
     */
    @PostMapping("/ner")
    public Mono<?> extractNer(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: NER 收到请求: {}", req);
        Map<String, Object> response = Map.of(
            "windNerPlugInfo", Map.of(
                "data", List.of(
                    Map.of("entityName", "贵州茅台", "entityType", "STOCK"),
                    Map.of("entityName", "阿里巴巴", "entityType", "STOCK")
                )
            )
        );
        return Mono.just(response).delayElement(Duration.ofMillis(200));
    }

    /**
     * 7. Viewpoint 观点可生成校验 Mock
     */
    @PostMapping("/viewpoint")
    public Mono<?> checkViewpoint(@RequestBody Map<String, Object> req) {
        log.info("Mock 下游: Viewpoint 收到请求: {}", req);
        Map<String, Object> response = Map.of(
            "outputs", Map.of("result", "True")
        );
        return Mono.just(response).delayElement(Duration.ofMillis(150));
    }

    private String buildLlmChunkJson(String content, String reasoning) {
        Map<String, Object> map = Map.of(
            "choices", List.of(
                Map.of("delta", Map.of(
                    "content", content,
                    "reasoning_content", reasoning
                ))
            )
        );
        return JSON.toJSONString(map);
    }
}
