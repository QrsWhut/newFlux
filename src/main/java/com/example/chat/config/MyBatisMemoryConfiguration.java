package com.example.chat.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MySQL 会话记忆的 MyBatis 与事务配置。
 */
@Configuration
@EnableTransactionManagement
@MapperScan("com.example.chat.dal.dao")
@ConditionalOnProperty(
        prefix = "agent.memory",
        name = "store-type",
        havingValue = AgentMemoryProperties.STORE_TYPE_MYSQL)
public class MyBatisMemoryConfiguration {
}
