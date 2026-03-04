package com.lreyes.platform.core.tenancy;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.Map;

/**
 * Hibernate pregunta "¿cuál es el tenant actual?" → leemos de {@link TenantContext}.
 * <p>
 * Implementa también {@link HibernatePropertiesCustomizer} para auto-registrarse
 * en las propiedades de Hibernate sin necesidad de configuración XML.
 * <p>
 * Si no hay tenant en el contexto (ej: startup, migraciones), devuelve "public".
 */
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
