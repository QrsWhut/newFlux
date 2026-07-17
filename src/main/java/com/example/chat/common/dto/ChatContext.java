package com.example.chat.common.dto;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 金融对话流转的上下文对象 (ChatContext)
 * 采用独占的 StringBuilder 替代 AtomicReference 频繁的字符串复制，消除 GC 压力；
 * 采用响应式管道进行流转，不在 Context 中存放共享的多线程 Reacting Mono 变量，保持干净不可变状态。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class ChatContext {

    private final ChatRequest request;
    private final AtomicReference<String> rewrittenQuestion = new AtomicReference<>("");
    private final AtomicReference<String> datasetContent = new AtomicReference<>("");
    private final AtomicReference<String> ragData = new AtomicReference<>("");
    private final AtomicReference<String> dpuData = new AtomicReference<>("");
    private final AtomicReference<String> askData = new AtomicReference<>("");
    private reactor.core.publisher.Mono<String> askMono;

    // 独占 StringBuilder 存放最终模型回答，用于后续 NER 与观点校验
    private final StringBuilder firstAnswer = new StringBuilder();
    private final StringBuilder secondAnswer = new StringBuilder();

    private final AtomicBoolean shouldCallSecond = new AtomicBoolean(true);
    private final AtomicLong sequence = new AtomicLong(0);

    private ChatContext(ChatRequest request) {
        this.request = request;
        // 初始情况下 rewrittenQuestion 为原始 question
        this.rewrittenQuestion.set(request.question());
    }

    /**
     * 工厂方法创建上下文
     */
    public static ChatContext create(ChatRequest request) {
        return new ChatContext(request);
    }

    public ChatRequest getRequest() {
        return request;
    }

    public String taskId() {
        return request.taskId();
    }

    public String sessionId() {
        return request.sessionId();
    }

    public String userId() {
        return request.userId();
    }

    /**
     * 获取当前自增 sequence
     */
    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public String getRewrittenQuestion() {
        return rewrittenQuestion.get();
    }

    public void setRewrittenQuestion(String question) {
        this.rewrittenQuestion.set(question);
    }

    public String getDatasetContent() {
        return datasetContent.get();
    }

    public void setDatasetContent(String content) {
        this.datasetContent.set(content);
    }

    public String getRagData() {
        return ragData.get();
    }

    public void setRagData(String data) {
        this.ragData.set(data);
    }

    public String getDpuData() {
        return dpuData.get();
    }

    public void setDpuData(String data) {
        this.dpuData.set(data);
    }

    public String getAskData() {
        return askData.get();
    }

    public void setAskData(String data) {
        this.askData.set(data);
    }

    public reactor.core.publisher.Mono<String> getAskMono() {
        return askMono;
    }

    public void setAskMono(reactor.core.publisher.Mono<String> askMono) {
        this.askMono = askMono;
    }

    public String getFirstAnswer() {
        synchronized (firstAnswer) {
            return firstAnswer.toString();
        }
    }

    public void appendFirstAnswer(String token) {
        synchronized (firstAnswer) {
            firstAnswer.append(token);
        }
    }

    public String getSecondAnswer() {
        synchronized (secondAnswer) {
            return secondAnswer.toString();
        }
    }

    public void appendSecondAnswer(String token) {
        synchronized (secondAnswer) {
            secondAnswer.append(token);
        }
    }

    public boolean isShouldCallSecond() {
        return shouldCallSecond.get();
    }

    public void setShouldCallSecond(boolean call) {
        this.shouldCallSecond.set(call);
    }

    /**
     * 判断是否需要做富化处理
     */
    public boolean needEnrichment() {
        return true;
    }
}
