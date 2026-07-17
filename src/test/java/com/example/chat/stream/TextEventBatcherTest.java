package com.example.chat.stream;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.enums.ChatEventType;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优化后使用 StepVerifier.withVirtualTime 验证 TextEventBatcher 状态机。
 * 彻底消除操作系统多线程调度与物理机器时钟不精确导致的测试偶发失败风险。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class TextEventBatcherTest {

    // 1. 文本达到32条立即刷新 (不需要 VirtualTime 也是立即的)
    @Test
    public void testFlushImmediatelyOn32Tokens() {
        List<ChatEvent> list = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            list.add(ChatEvent.text("task-1", (long) i, "A", "contentFirst"));
        }

        Flux<ChatEvent> batched = TextEventBatcher.batch(Flux.fromIterable(list));

        StepVerifier.create(batched)
                // 35 个 Token 被拆分为两批：第一批攒满 32 个合并，第二批剩余 3 个合并
                .expectNextMatches(event -> {
                    String content = ((ChatEvent.TextDelta) event.payload()).content();
                    return content.length() == 32;
                })
                .expectNextMatches(event -> {
                    String content = ((ChatEvent.TextDelta) event.payload()).content();
                    return content.length() == 3;
                })
                .verifyComplete();
    }

    // 2. 文本不足32条，50ms后刷新 (采用 VirtualTime 验证)
    @Test
    public void testFlushOnTimeout() {
        StepVerifier.withVirtualTime(() -> TextEventBatcher.batch(
                Flux.concat(
                        Flux.just(ChatEvent.text("task-2", 1L, "A", "contentFirst")),
                        Mono.delay(Duration.ofMillis(100)).thenMany(Flux.just(ChatEvent.text("task-2", 2L, "B", "contentFirst")))
                )
        ))
        .expectSubscription()
        // 49ms 之内没有任何输出事件
        .expectNoEvent(Duration.ofMillis(49))
        // 推进 5ms 刚好过 50ms 窗口，触发 A 的超时合并下发
        .thenAwait(Duration.ofMillis(5))
        .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
        // 推进 100ms 触发 B 到达并超时 50ms 合并下发
        .thenAwait(Duration.ofMillis(100))
        .expectNextMatches(event -> "B".equals(((ChatEvent.TextDelta) event.payload()).content()))
        .verifyComplete();
    }

    // 3. 文本后1ms到达STATUS，STATUS不等待50ms (采用 VirtualTime 验证)
    @Test
    public void testStatusNoDelay() {
        StepVerifier.withVirtualTime(() -> TextEventBatcher.batch(
                Flux.concat(
                        Flux.just(ChatEvent.text("task-3", 1L, "A", "contentFirst")),
                        Mono.delay(Duration.ofMillis(1)).thenMany(Flux.just(ChatEvent.status("task-3", 2L, "检索中")))
                )
        ))
        .expectSubscription()
        // 推进 1ms 使 STATUS 达到，STATUS 会瞬间 complete 前置文本窗口，
        // 使得 "A" 与 STATUS 在 1ms 的瞬间全部被 Flush 发出，绝不延迟 50ms
        .thenAwait(Duration.ofMillis(1))
        .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
        .expectNextMatches(event -> event.type() == ChatEventType.STATUS && "检索中".equals(((ChatEvent.StatusPayload) event.payload()).message()))
        .verifyComplete();
    }

    // 4. STATUS前的文本必须先输出
    @Test
    public void testTextBeforeStatusFirst() {
        Flux<ChatEvent> source = Flux.just(
                ChatEvent.text("task-4", 1L, "A", "contentFirst"),
                ChatEvent.status("task-4", 2L, "就绪")
        );

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS)
                .verifyComplete();
    }

    // 5. ERROR前的文本必须先输出
    @Test
    public void testTextBeforeErrorFirst() {
        ChatEvent errorEvent = ChatEvent.error("task-5", 2L, "500", "LLM异常");
        Flux<ChatEvent> source = Flux.just(
                ChatEvent.text("task-5", 1L, "A", "contentFirst"),
                errorEvent
        );

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> event.type() == ChatEventType.ERROR)
                .verifyComplete();
    }

    // 6. COMPLETE前的文本必须先输出
    @Test
    public void testTextBeforeCompleteFirst() {
        Flux<ChatEvent> source = Flux.just(
                ChatEvent.text("task-6", 1L, "A", "contentFirst"),
                ChatEvent.complete("task-6", 2L)
        );

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> event.terminal()) // COMPLETE
                .verifyComplete();
    }

    // 7. CANCELLED前的文本必须先输出
    @Test
    public void testTextBeforeCancelFirst() {
        ChatEvent cancelEvent = new ChatEvent("task-7", 2L, ChatEventType.STATUS, java.time.Instant.now(), new ChatEvent.StatusPayload("CANCELLED"), true);
        Flux<ChatEvent> source = Flux.just(
                ChatEvent.text("task-7", 1L, "A", "contentFirst"),
                cancelEvent
        );

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> "CANCELLED".equals(((ChatEvent.StatusPayload) event.payload()).message()))
                .verifyComplete();
    }

    // 8. 连续多个状态事件直通测试
    @Test
    public void testMultipleSequentialStatuses() {
        Flux<ChatEvent> source = Flux.just(
                ChatEvent.text("task-8", 1L, "A", "contentFirst"),
                ChatEvent.status("task-8", 2L, "S1"),
                ChatEvent.status("task-8", 3L, "S2"),
                ChatEvent.complete("task-8", 4L)
        );

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS && "S1".equals(((ChatEvent.StatusPayload) event.payload()).message()))
                .expectNextMatches(event -> event.type() == ChatEventType.STATUS && "S2".equals(((ChatEvent.StatusPayload) event.payload()).message()))
                .expectNextMatches(event -> event.terminal())
                .verifyComplete();
    }

    // 9. 真实 Reactor 物理取消及取消传播测试 (P0-3)
    @Test
    public void testRealReactorCancelPropagation() {
        AtomicBoolean upstreamCancelled = new AtomicBoolean(false);

        // 创建一个永远不完结的上游流，以便测试取消
        Flux<ChatEvent> source = Flux.just(ChatEvent.text("task-cancel", 1L, "A", "contentFirst"))
                .concatWith(Flux.never())
                .doOnCancel(() -> upstreamCancelled.set(true));

        Flux<ChatEvent> batched = TextEventBatcher.batch(source);

        StepVerifier.create(batched)
                .thenRequest(1)
                // 主动发起物理 Cancel 订阅信号
                .thenCancel()
                .verify();

        // 断言上游流成功接收到了向下传递的 cancel 信号，定时器随之自动注销，无泄露
        assertTrue(upstreamCancelled.get(), "TextEventBatcher cancel 信号必须逆向传播回上游 Flux");
    }

    // 10. 多路定时器生命周期与合并完整性验证
    @Test
    public void testMultipleTimerLifecycle() {
        StepVerifier.withVirtualTime(() -> TextEventBatcher.batch(
                Flux.concat(
                        Flux.just(ChatEvent.text("task-10", 1L, "A", "contentFirst")),
                        Mono.delay(Duration.ofMillis(80)).thenMany(Flux.just(ChatEvent.text("task-10", 2L, "B", "contentFirst"))),
                        Mono.delay(Duration.ofMillis(80)).thenMany(Flux.just(ChatEvent.text("task-10", 3L, "C", "contentFirst")))
                )
        ))
        .expectSubscription()
        .thenAwait(Duration.ofMillis(250))
        .expectNextMatches(event -> "A".equals(((ChatEvent.TextDelta) event.payload()).content()))
        .expectNextMatches(event -> "B".equals(((ChatEvent.TextDelta) event.payload()).content()))
        .expectNextMatches(event -> "C".equals(((ChatEvent.TextDelta) event.payload()).content()))
        .verifyComplete();
    }
}
