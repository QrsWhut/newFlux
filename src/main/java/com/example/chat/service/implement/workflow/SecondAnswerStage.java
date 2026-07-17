package com.example.chat.service.implement.workflow;

import com.example.chat.common.dto.ChatContext;
import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.dto.downstream.LlmRequest;
import com.example.chat.integration.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流阶段：流式次轮大模型对话 (SecondAnswerStage)
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Component
public class SecondAnswerStage {

    private final LlmClient llmClient;

    public SecondAnswerStage(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 执行次轮大模型流式对话。此时 RAG/DPU 数据均在 context 中准备就绪。
     */
    public Flux<ChatEvent> execute(ChatContext context) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        Map<String, Object> promptParams = new HashMap<>();
        promptParams.put("lstquestion", context.getRewrittenQuestion());
        promptParams.put("content", context.getDatasetContent());
        promptParams.put("currentDate", currentDate);
        // 读取已经在第一轮异步拿到的 RAG 与 DPU 富化知识
        promptParams.put("ragdata", context.getRagData());
        promptParams.put("dpudata", context.getDpuData());
        
        String pageData = (String) context.getRequest().attributes().getOrDefault("pageData", "");
        promptParams.put("pageData", pageData);

        LlmRequest request = new LlmRequest("P052184", true, promptParams);

        log.info("开始执行次轮大模型流式输出阶段, taskId={}", context.taskId());

        return llmClient.stream(request, context.sessionId())
                .map(chunk -> {
                    String text = chunk.text();
                    context.appendSecondAnswer(text);
                    return ChatEvent.text(context.taskId(), context.nextSequence(), text, "contentSecond");
                })
                .concatWith(Flux.defer(() -> {
                    context.appendSecondAnswer("\n");
                    return Flux.just(ChatEvent.text(context.taskId(), context.nextSequence(), "\n", "contentSecond"));
                }));
    }
}
