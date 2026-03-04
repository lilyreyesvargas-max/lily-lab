package com.lreyes.platform.core.tenancy;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clave compuesta para {@link RoleSchema}.
 */
public class RoleSchemaId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID roleId;
    private String schemaType;

    public RoleSchemaId() {}

    public RoleSchemaId(UUID roleId, String schemaType) {
        this.roleId = roleId;
        this.schemaType = schemaType;
    }

    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public String getSchemaType() { return schemaType; }
    public void setSchemaType(String schemaType) { this.schemaType = schemaType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleSchemaId that = (RoleSchemaId) o;
        return Objects.equals(roleId, that.roleId)
                && Objects.equals(schemaType, that.schemaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, schemaType);
    }
}
