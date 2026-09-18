package com.example.chat.task;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务取消服务安全隔离测试。
 */
class TaskCancellationServiceTest {

    /**
     * 验证同一用户同一任务可注册并取消多个订阅句柄。
     */
    @Test
    void shouldCancelEveryRegistrationForSameTask() {
        TaskCancellationService service = new TaskCancellationService();
        AtomicBoolean firstDisposed = new AtomicBoolean();
        AtomicBoolean secondDisposed = new AtomicBoolean();
        service.register("user-001", "task-001", disposable(firstDisposed));
        service.register("user-001", "task-001", disposable(secondDisposed));

        boolean cancelled = service.cancel("user-001", "task-001");

        assertTrue(cancelled);
        assertTrue(firstDisposed.get());
        assertTrue(secondDisposed.get());
        assertFalse(service.cancel("user-001", "task-001"));
    }

    /**
     * 验证注销当前句柄不会误删同任务的其他订阅。
     */
    @Test
    void shouldRemoveOnlyCurrentRegistration() {
        TaskCancellationService service = new TaskCancellationService();
        AtomicBoolean firstDisposed = new AtomicBoolean();
        AtomicBoolean secondDisposed = new AtomicBoolean();
        TaskCancellationService.Registration firstRegistration =
                service.register("user-001", "task-001", disposable(firstDisposed));
        service.register("user-001", "task-001", disposable(secondDisposed));

        assertTrue(service.remove(firstRegistration));
        assertTrue(service.cancel("user-001", "task-001"));
        assertFalse(firstDisposed.get());
        assertTrue(secondDisposed.get());
    }

    /**
     * 验证相同任务标识在不同用户之间互相隔离。
     */
    @Test
    void shouldRejectCrossUserCancellation() {
        TaskCancellationService service = new TaskCancellationService();
        AtomicBoolean ownerDisposed = new AtomicBoolean();
        service.register("owner-user", "shared-task", disposable(ownerDisposed));

        assertFalse(service.cancel("attacker-user", "shared-task"));
        assertFalse(ownerDisposed.get());
        assertTrue(service.cancel("owner-user", "shared-task"));
        assertTrue(ownerDisposed.get());
    }

    /**
     * 创建可观察释放状态的句柄。
     *
     * @param disposed 释放状态
     * @return 取消句柄
     */
    private Disposable disposable(AtomicBoolean disposed) {
        return () -> disposed.set(true);
    }
}