package com.example.chat.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MySQL 记忆模式所需的数据源、MyBatis 与事务基础设施。
 */
@Configuration
@ConditionalOnProperty(
        prefix = "agent.memory",
        name = "store-type",
        havingValue = AgentMemoryProperties.STORE_TYPE_MYSQL)
public class MyBatisMemoryRuntimeConfiguration {

    /** Mapper XML 资源位置。 */
    private static final String MAPPER_LOCATION_PATTERN = "classpath*:mapper/*.xml";

    /**
     * 绑定 MySQL 数据源基础属性。
     *
     * @return 数据源属性
     */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties memoryDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 仅在 MySQL 记忆模式下创建 Hikari 数据源。
     *
     * @param properties 数据源属性
     * @return MySQL 数据源
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource memoryDataSource(DataSourceProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("MySQL 记忆模式必须配置 spring.datasource.url");
        }
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 创建显式 MyBatis SqlSessionFactory。
     *
     * @param dataSource MySQL 数据源
     * @return SqlSessionFactory
     * @throws Exception SqlSessionFactory 初始化失败时抛出
     */
    @Bean
    @ConditionalOnMissingBean(SqlSessionFactory.class)
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setUseGeneratedKeys(true);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources(MAPPER_LOCATION_PATTERN));
        return factoryBean.getObject();
    }

    /**
     * 创建 MyBatis SqlSessionTemplate。
     *
     * @param sqlSessionFactory SqlSessionFactory
     * @return SqlSessionTemplate
     */
    @Bean
    @ConditionalOnMissingBean(SqlSessionTemplate.class)
    public SqlSessionTemplate sqlSessionTemplate(
            SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 创建阻塞 JDBC 短事务管理器。
     *
     * @param dataSource MySQL 数据源
     * @return 平台事务管理器
     */
    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager memoryTransactionManager(
            DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
