package com.lreyes.platform.core.tenancy.platform;

import java.time.Instant;

/**
 * POJO para la tabla {@code platform.tenant_schemas}.
 * No es entidad JPA — se accede vía JDBC.
 */
public class TenantSchema {

    private Integer id;
    private Integer tenantId;
    private String schemaName;
    private String schemaType;
    private boolean active;
    private Instant createdAt;

    public TenantSchema() {}

    public TenantSchema(Integer tenantId, String schemaName, String schemaType) {
        this.tenantId = tenantId;
        this.schemaName = schemaName;
        this.schemaType = schemaType;
        this.active = true;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getSchemaType() { return schemaType; }
    public void setSchemaType(String schemaType) { this.schemaType = schemaType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
