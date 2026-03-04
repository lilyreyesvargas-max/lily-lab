package com.lreyes.platform.core.tenancy.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * POJO para la tabla {@code platform.platform_users}.
 * No es entidad JPA — se accede vía JDBC.
 */
public class PlatformUser {

    private UUID id;
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public PlatformUser() {}

    public PlatformUser(String username, String passwordHash, String email, String fullName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.enabled = true;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
