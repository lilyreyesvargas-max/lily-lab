package com.lreyes.platform;

import com.zaxxer.hikari.HikariDataSource;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Proporciona un DataSource separado para Flowable en el perfil "test".
 * <p>
 * Usa un H2 in-memory sin MODE=PostgreSQL para que Flowable pueda
 * crear sus tablas (Liquibase usa BLOB que no es compatible con MODE=PostgreSQL).
 */
@Configuration
@Profile("test")
class TestFlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableTestDataSourceConfigurer() {
        return engineConfig -> {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:flowable_test;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setDriverClassName("org.h2.Driver");
            ds.setPoolName("FlowableTestPool");
            ds.setMaximumPoolSize(5);
            engineConfig.setDataSource(ds);
        };
    }
}
