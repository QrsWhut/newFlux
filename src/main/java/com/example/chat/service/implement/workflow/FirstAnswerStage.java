package com.example.chat.service.implement.workflow;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.downstream.LlmRequest;
import com.example.chat.integration.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流阶段：流式首轮大模型对话 (FirstAnswerStage)
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class FirstAnswerStage {

    private final LlmClient llmClient;

    public FirstAnswerStage(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 执行首轮流式大模型生成，并拦截 {#} 和 #end# 标记
     */
    public Flux<ChatEvent> execute(ChatContext context) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        Map<String, Object> promptParams = new HashMap<>();
        promptParams.put("question", context.getRewrittenQuestion());
        promptParams.put("content", context.getDatasetContent());
        promptParams.put("currentDate", currentDate);
        // 如果 attributes 含有 pageData，进行映射
        String pageData = (String) context.getRequest().attributes().getOrDefault("pageData", "");
        promptParams.put("pageData", pageData);
        // 传递 RAG/DPU 数据（首轮若无可传空字符串）
        promptParams.put("lstrag", "");
        promptParams.put("lstdpu", "");

        LlmRequest request = new LlmRequest("P053102", true, promptParams);

        log.info("开始执行首轮大模型流式输出阶段, taskId={}", context.taskId());

        return Flux.defer(() -> {
            // 使用 StringBuilder 缓冲字符做高精度前缀拦截过滤
            StringBuilder markBuffer = new StringBuilder();
            
            return llmClient.stream(request, context.sessionId())
                    .handle((com.example.chat.common.dto.downstream.LlmChunk chunk, SynchronousSink<ChatEvent> sink) -> {
                        String text = chunk.text();
                        if (text == null || text.isEmpty()) {
                            return;
                        }
                        
                        StringBuilder toSend = new StringBuilder();
                        for (char c : text.toCharArray()) {
                            markBuffer.append(c);
                            if ("#end#".contentEquals(markBuffer)) {
                                // 匹配到跳过次轮标识
                                log.info("首段流中识别到 '#end#' 标识，次轮大模型生成将跳过, taskId={}", context.taskId());
                                context.setShouldCallSecond(false);
                                markBuffer.setLength(0);
                            } else if ("{#}".contentEquals(markBuffer)) {
                                // 匹配到进入次轮标识
                                log.info("首段流中识别到 '{#}' 标识，需要启动次轮大模型生成, taskId={}", context.taskId());
                                context.setShouldCallSecond(true);
                                markBuffer.setLength(0);
                            } else {
                                char firstChar = markBuffer.charAt(0);
                                if (firstChar != '#' && firstChar != '{') {
                                    // 第一个字符不是特殊标记的起始符，直接安全下发整个缓冲区
                                    toSend.append(markBuffer.toString());
                                    markBuffer.setLength(0);
                                } else if (!"#end#".startsWith(markBuffer.toString()) && !"{#}".startsWith(markBuffer.toString())) {
                                    // 虽是标记的开头，但当前组合已经不可能是有效标记的前缀，整体作为普通字符下发
                                    toSend.append(markBuffer.toString());
                                    markBuffer.setLength(0);
                                }
                            }
                        }
                        
                        if (toSend.length() > 0) {
                            String sendStr = toSend.toString();
                            context.appendFirstAnswer(sendStr);
                            sink.next(ChatEvent.text(context.taskId(), context.nextSequence(), sendStr, "contentFirst"));
                        }
                    });
        })
        .concatWith(Flux.defer(() -> {
            // 首段结束后下发换行符
            context.appendFirstAnswer("\n");
            return Flux.just(ChatEvent.text(context.taskId(), context.nextSequence(), "\n", "contentFirst"));
        }));
    }
}
