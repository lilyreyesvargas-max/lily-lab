package com.lreyes.platform.core.tenancy;

import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Propiedades de configuración de tenants.
 * <p>
 * Lee de application-*.yml:
 * <pre>
 * app:
 *   tenants:
 *     - acme
 *     - globex
 * </pre>
 * <p>
 * {@link #isRegisteredTenant(String)} consulta primero la BD (vía {@link TenantRegistryService})
 * y hace fallback a la lista YAML si el servicio no está disponible.
 */
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class TenantProperties {

    /** Patrón válido para nombres de tenant: alfanumérico + guión bajo, 2-63 chars */
    private static final Pattern VALID_TENANT = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");

    /** Lista de tenants registrados (YAML) */
    private List<String> tenants = new ArrayList<>();

    @Autowired(required = false)
    private TenantRegistryService tenantRegistryService;

    /**
     * Valida que un tenant ID es sintácticamente correcto (previene SQL injection en SET search_path).
     */
    public static boolean isValidTenantName(String tenant) {
        return tenant != null && VALID_TENANT.matcher(tenant).matches();
    }

    /**
     * Verifica si un tenant está registrado.
     * Primero consulta BD (cache), luego fallback a YAML.
     */
    public boolean isRegisteredTenant(String tenant) {
        if (tenantRegistryService != null) {
            return tenantRegistryService.isRegisteredTenant(tenant);
        }
        return tenants.contains(tenant);
    }
}
