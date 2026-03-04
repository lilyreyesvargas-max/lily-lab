package com.lreyes.platform.core.tenancy.platform;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configura un DataSource y JdbcTemplate para el schema {@code platform}.
 * <p>
 * Sigue el mismo patrón que {@code FlowableDatasourceConfig}: el DataSource
 * NO se registra como @Bean para no interferir con el DataSource primario
 * auto-configurado por Spring Boot.
 * <p>
 * El JdbcTemplate sí se expone como bean cualificado {@code "platformJdbc"}.
 */
@Configuration
@Profile({"dev", "local", "prod"})
@Slf4j
public class PlatformJdbcConfig {

    private DataSource platformDataSource;

    @Bean("platformJdbc")
    public JdbcTemplate platformJdbcTemplate(
            @Value("${spring.datasource-platform.url}") String url,
            @Value("${spring.datasource-platform.username}") String username,
            @Value("${spring.datasource-platform.password}") String password,
            @Value("${spring.datasource-platform.driver-class-name}") String driver) {

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driver);
        ds.setPoolName("PlatformPool");
        ds.setMaximumPoolSize(5);

        this.platformDataSource = ds;

        log.info("Platform JdbcTemplate configurado con pool 'PlatformPool', url='{}'", url);
        return new JdbcTemplate(ds);
    }

    /**
     * Retorna el DataSource del schema platform (para Flyway).
     * Solo disponible después de que se haya creado el bean platformJdbc.
     */
    public DataSource getPlatformDataSource() {
        return platformDataSource;
    }
}
