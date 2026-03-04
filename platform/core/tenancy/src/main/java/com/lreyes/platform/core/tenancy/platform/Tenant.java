package com.lreyes.platform.core.tenancy.platform;

import java.time.Instant;

/**
 * POJO para la tabla {@code platform.tenants}.
 * No es entidad JPA — se accede vía JDBC.
 */
public class Tenant {

    private Integer id;
    private String name;
    private String displayName;
    private boolean active;
    private String primaryColor;
    private String logoPath;
    private Instant createdAt;
    private Instant updatedAt;

    public Tenant() {}

    public Tenant(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
        this.active = true;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
