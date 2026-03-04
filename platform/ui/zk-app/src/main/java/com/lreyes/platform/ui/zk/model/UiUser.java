package com.lreyes.platform.ui.zk.model;

import java.io.Serializable;
import java.util.List;

/**
 * Representa al usuario autenticado en la sesión ZK.
 * Se almacena en {@code Sessions.getCurrent().setAttribute("user", uiUser)}.
 */
public class UiUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String username;
    private final String tenantId;
    private final List<String> roles;
    private final boolean platformAdmin;

    public UiUser(String username, String tenantId, List<String> roles) {
        this(username, tenantId, roles, false);
    }

    public UiUser(String username, String tenantId, List<String> roles, boolean platformAdmin) {
        this.username = username;
        this.tenantId = tenantId;
        this.roles = roles;
        this.platformAdmin = platformAdmin;
    }

    public String getUsername() { return username; }
    public String getTenantId() { return tenantId; }
    public List<String> getRoles() { return roles; }
    public boolean isPlatformAdmin() { return platformAdmin; }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public String getDisplayName() {
        if (platformAdmin) {
            return username + " [PLATFORM]";
        }
        return username + " [" + tenantId + "]";
    }
}
