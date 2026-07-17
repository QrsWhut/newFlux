package com.example.chat.stream;

import com.example.chat.common.dto.ChatEvent;
import com.example.chat.common.enums.ChatEventType;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 连续文本流事件批处理合并器
 * 采用 100% 反应式原生背压安全的 windowUntil + switchOnFirst 顺序算子链重新实现。
 * 绝无手动订阅与 Long.MAX_VALUE 绕过背压隐患，取消与背压信号完美传导上游。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
public class TextEventBatcher {

    private static final int BATCH_SIZE = 32;
    private static final long BATCH_TIMEOUT_MS = 50;

    /**
     * 对源事件流进行高精度、不破坏相对时序且背压自适应的原生连续文本合并与直通
     */
    public static Flux<ChatEvent> batch(Flux<ChatEvent> source) {
        return source.windowUntil(event -> event.type() != ChatEventType.TEXT_DELTA, true)
                .concatMap(window -> window.switchOnFirst((signal, innerFlux) -> {
                    ChatEvent first = signal.get();
                    if (first == null) {
                        return innerFlux;
                    }

                    if (first.type() != ChatEventType.TEXT_DELTA) {
                        // 1. 如果第一个元素是非文本控制信号（如 STATUS / ERROR / COMPLETE / CANCELLED），
                        //    我们使用 Flux.concat 将其立即无延迟下发，剩下的文本后续进入 bufferTimeout 延迟批处理。
                        Flux<ChatEvent> firstFlow = Flux.just(first);
                        Flux<ChatEvent> textFlow = innerFlux.skip(1)
                                .bufferTimeout(BATCH_SIZE, Duration.ofMillis(BATCH_TIMEOUT_MS))
                                .filter(list -> !list.isEmpty())
                                .map(TextEventBatcher::mergeTextBatch);

                        return Flux.concat(firstFlow, textFlow);
                    } else {
                        // 2. 如果第一个元素是文本，说明整段 Window 子流全部是连续文本，直接延迟批处理
                        return innerFlux.bufferTimeout(BATCH_SIZE, Duration.ofMillis(BATCH_TIMEOUT_MS))
                                .filter(list -> !list.isEmpty())
                                .map(TextEventBatcher::mergeTextBatch);
                    }
                }));
    }

    /**
     * 将一批连续的 TEXT_DELTA 合并为单个 ChatEvent
     */
    private static ChatEvent mergeTextBatch(List<ChatEvent> batch) {
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("Batch cannot be empty");
        }

        ChatEvent first = batch.get(0);
        String taskId = first.taskId();
        long sequence = first.sequence();

        // 拼接 content 和 reasoningContent
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        String head = "";

        for (ChatEvent event : batch) {
            if (event.payload() instanceof ChatEvent.TextDelta delta) {
                if (delta.content() != null) {
                    contentBuilder.append(delta.content());
                }
                if (delta.reasoningContent() != null) {
                    reasoningBuilder.append(delta.reasoningContent());
                }
                if (delta.head() != null && !delta.head().isEmpty()) {
                    head = delta.head();
                }
            }
        }

        return new ChatEvent(
                taskId,
                sequence,
                ChatEventType.TEXT_DELTA,
                first.timestamp(),
                new ChatEvent.TextDelta(contentBuilder.toString(), reasoningBuilder.toString(), head),
                false
        );
    }
}
