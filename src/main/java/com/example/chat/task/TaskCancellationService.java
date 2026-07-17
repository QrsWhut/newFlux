package com.example.chat.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式任务 cancellation 服务
 * 负责记录活跃任务的 Disposable 句柄，当客户端断开或者明确触发取消时，主动熔断下游的网络连接。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Slf4j
@Service
public class TaskCancellationService {

    private final ConcurrentHashMap<String, Disposable> activeTasks = new ConcurrentHashMap<>();

    /**
     * 注册当前正在执行的任务及其取消句柄
     *
     * @param taskId     任务唯一 ID
     * @param disposable 取消句柄
     */
    public void register(String taskId, Disposable disposable) {
        if (taskId == null || disposable == null) {
            return;
        }
        activeTasks.put(taskId, disposable);
        log.info("任务已注册取消句柄, taskId={}", taskId);
    }

    /**
     * 移除任务的取消句柄（通常在任务正常/异常结束后执行，防内存泄漏）
     *
     * @param taskId 任务唯一 ID
     */
    public void remove(String taskId) {
        if (taskId == null) {
            return;
        }
        activeTasks.remove(taskId);
        log.info("任务取消句柄已注销, taskId={}", taskId);
    }

    /**
     * 触发指定任务的主动取消和熔断
     *
     * @param taskId 任务唯一 ID
     */
    public void cancel(String taskId) {
        if (taskId == null) {
            return;
        }
        Disposable disposable = activeTasks.remove(taskId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.warn("任务已被主动取消，下游连接已熔断释放, taskId={}", taskId);
        }
    }
}
