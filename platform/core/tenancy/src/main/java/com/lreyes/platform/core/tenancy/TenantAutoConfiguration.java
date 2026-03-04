package com.lreyes.platform.core.tenancy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Auto-configuración del módulo de tenancy.
 * <p>
 * Registra los beans necesarios para que Hibernate funcione en modo multi-tenant por schema:
 * <ul>
 *   <li>{@link TenantIdentifierResolver}: resuelve el tenant actual desde el ThreadLocal.</li>
 *   <li>{@link SchemaMultiTenantConnectionProvider}: cambia el search_path de la conexión.</li>
 * </ul>
 * <p>
 * Ambos implementan {@link org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer}
 * para auto-inyectarse en las propiedades de Hibernate sin configuración adicional.
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@Slf4j
public class TenantAutoConfiguration {

    @Bean
    public TenantIdentifierResolver tenantIdentifierResolver() {
        log.info("Registrando TenantIdentifierResolver (multi-tenant por schema)");
        return new TenantIdentifierResolver();
    }

    @Bean
    public SchemaMultiTenantConnectionProvider schemaMultiTenantConnectionProvider(DataSource dataSource) {
        log.info("Registrando SchemaMultiTenantConnectionProvider");
        return new SchemaMultiTenantConnectionProvider(dataSource);
    }
}
