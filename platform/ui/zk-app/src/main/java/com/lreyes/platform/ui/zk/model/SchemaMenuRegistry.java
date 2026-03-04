package com.lreyes.platform.ui.zk.model;

import com.lreyes.platform.core.authsecurity.RoleConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro estatico que mapea schema_type a items de menu.
 * <p>
 * Los items de admin y platform_admin se agregan aparte segun el rol del usuario.
 */
public final class SchemaMenuRegistry {

    private static final Map<String, List<MenuItem>> SCHEMA_MENU_MAP = new LinkedHashMap<>();

    static {
        SCHEMA_MENU_MAP.put("core", List.of());

        SCHEMA_MENU_MAP.put("hr", List.of(
                new MenuItem("employees", "Empleados", "~./zul/employees/list.zul")
        ));

        SCHEMA_MENU_MAP.put("sales", List.of(
                new MenuItem("customers", "Clientes", "~./zul/customers/list.zul"),
                new MenuItem("tasks", "Procesos / Tareas", "~./zul/workflow/tasks.zul")
        ));

        SCHEMA_MENU_MAP.put("documents", List.of());
    }

    /**
     * Retorna el item de Dashboard (siempre visible, no depende de schema).
     */
    public static MenuItem getDashboardItem() {
        return new MenuItem("dashboard", "Dashboard", "~./zul/dashboard.zul");
    }

    private SchemaMenuRegistry() {}

    /**
     * Retorna los items de menu correspondientes a los schema_types permitidos.
     */
    public static List<MenuItem> getMenuItems(Set<String> allowedSchemaTypes) {
        List<MenuItem> items = new ArrayList<>();
        for (Map.Entry<String, List<MenuItem>> entry : SCHEMA_MENU_MAP.entrySet()) {
            if (allowedSchemaTypes.contains(entry.getKey())) {
                items.addAll(entry.getValue());
            }
        }
        return items;
    }

    /**
     * Items de administracion del tenant (visibles para rol admin).
     */
    public static List<MenuItem> getAdminItems() {
        return List.of(
                new MenuItem("roles", "Roles", "~./zul/admin/roles.zul", RoleConstants.ADMIN),
                new MenuItem("users", "Usuarios", "~./zul/admin/users.zul", RoleConstants.ADMIN),
                new MenuItem("catalogs", "Catalogos", "~./zul/admin/catalogs.zul", RoleConstants.ADMIN),
                new MenuItem("role-schemas", "Permisos", "~./zul/admin/role-schemas.zul", RoleConstants.ADMIN)
        );
    }

    /**
     * Items de administracion de la plataforma (visibles para platform_admin).
     */
    public static List<MenuItem> getPlatformAdminItems() {
        return List.of(
                new MenuItem("tenants", "Tenants", "~./zul/platform/tenants.zul", RoleConstants.PLATFORM_ADMIN),
                new MenuItem("schemas", "Schemas", "~./zul/platform/schemas.zul", RoleConstants.PLATFORM_ADMIN),
                new MenuItem("platform-users", "Admins Plataforma", "~./zul/platform/platform-users.zul", RoleConstants.PLATFORM_ADMIN)
        );
    }

    /**
     * Retorna todos los schema_types registrados en el registry.
     */
    public static Set<String> getRegisteredSchemaTypes() {
        return SCHEMA_MENU_MAP.keySet();
    }
}
