package com.lreyes.platform.core.workflow;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Configura un DataSource secundario para Flowable.
 * <p>
 * Solo se activa en perfiles {@code local} y {@code prod}. En estos perfiles,
 * Flowable usa el schema {@code platform} (compartido), mientras que las tablas
 * de negocio viven en schemas de tenant ({@code acme}, {@code globex}, etc.).
 * <p>
 * IMPORTANTE: El DataSource de Flowable NO se registra como @Bean para no
 * interferir con el DataSource primario auto-configurado por Spring Boot
 * (que usa DataSourceAutoConfiguration con @ConditionalOnMissingBean).
 */
@Configuration
@Profile({"dev", "local", "prod"})
@Slf4j
public class FlowableDatasourceConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurer(
            @Value("${spring.datasource-flowable.url}") String url,
            @Value("${spring.datasource-flowable.username}") String username,
            @Value("${spring.datasource-flowable.password}") String password,
            @Value("${spring.datasource-flowable.driver-class-name}") String driver) {

        return engineConfig -> {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName(driver);
            ds.setPoolName("FlowablePool");
            ds.setMaximumPoolSize(5);
            // Garantiza que el search_path apunte al schema 'platform' donde Flowable
            // crea sus tablas act_*, independientemente del search_path por defecto del servidor.
            ds.setConnectionInitSql("SET search_path TO platform");

            log.info("Flowable EngineConfigurer: DataSource separado con pool 'FlowablePool', url='{}'", url);
            engineConfig.setDataSource(ds);
            engineConfig.setTransactionManager(new DataSourceTransactionManager(ds));
        };
    }
}
