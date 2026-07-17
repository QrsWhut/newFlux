package com.example.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 虚拟线程池调度器配置中心
 * 专门用于为任何可能存在阻塞等待的第三方 SDK 或者落后旧网关层提供独立并发边界限制。
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@Configuration
public class VirtualThreadConfig {

    /**
     * 创建基于 JDK 21 虚拟线程的 Scheduler，并在 Bean 销毁时安全关闭底层 Executor
     */
    @Bean(destroyMethod = "dispose")
    public Scheduler blockingScheduler() {
        ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("blocking-adapter-", 0).factory()
        );
        return Schedulers.fromExecutorService(executor);
    }
}
