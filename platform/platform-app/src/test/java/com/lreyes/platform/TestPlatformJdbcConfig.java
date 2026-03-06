package com.lreyes.platform;

import com.lreyes.platform.core.tenancy.platform.PlatformJdbcConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configuración de test que reemplaza {@link PlatformJdbcConfig} para el perfil "test".
 * <p>
 * Usa el mismo DataSource H2 primario (en lugar de spring.datasource-platform.*)
 * para proporcionar el bean {@code platformJdbc} y {@link #getPlatformDataSource()}.
 */
@TestConfiguration
@Profile("test")
public class TestPlatformJdbcConfig extends PlatformJdbcConfig {

    @Bean("platformJdbc")
    @Override
    public JdbcTemplate platformJdbcTemplate(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name}") String driver) {
        // Point platform pool at the PLATFORM schema so queries find tenants, platform_users, etc.
        String platformUrl = url.contains(":h2:") ? url + ";SCHEMA=PLATFORM" : url;
        return super.platformJdbcTemplate(platformUrl, username, password, driver);
    }
}
