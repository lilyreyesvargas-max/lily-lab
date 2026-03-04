package com.lreyes.platform.core.authsecurity;

/**
 * Roles base del sistema (RBAC).
 * <p>
 * Corresponden a los roles seed insertados por {@code V1__core_schema.sql}.
 * En Spring Security se usan como {@code ROLE_admin}, {@code ROLE_gestor}, etc.
 */
public final class RoleConstants {

    public static final String ADMIN = "admin";
    public static final String GESTOR = "gestor";
    public static final String OPERADOR = "operador";
    public static final String AUDITOR = "auditor";
    public static final String PLATFORM_ADMIN = "platform_admin";

    /** Prefijo que Spring Security agrega automáticamente */
    public static final String ROLE_PREFIX = "ROLE_";

    private RoleConstants() {}
}
