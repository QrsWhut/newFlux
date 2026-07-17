package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AB1对话流式服务启动类
 * 采用完全响应式的 Spring WebFlux 架构体系构建
 *
 * @author Antigravity
 * @since 2026-07-17
 */
@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }
}
