package com.lreyes.platform.core.tenancy;

import java.util.Optional;

/**
 * Almacena el identificador del tenant actual en un ThreadLocal.
 * <p>
 * Flujo típico:
 * <ol>
 *   <li>{@link TenantFilter} extrae el tenant del request y llama {@link #setCurrentTenant}</li>
 *   <li>Hibernate usa {@link TenantIdentifierResolver} que lee de aquí</li>
 *   <li>{@link TenantFilter} llama {@link #clear()} en el finally</li>
 * </ol>
 * <p>
 * IMPORTANTE: siempre limpiar con {@link #clear()} para evitar leaks entre requests.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT = "public";

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return Optional.ofNullable(CURRENT_TENANT.get()).orElse(DEFAULT_TENANT);
    }

    public static Optional<String> getCurrentTenantOptional() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
