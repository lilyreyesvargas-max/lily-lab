package com.lreyes.platform.core.tenancy.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la tabla {@code platform.tenants}.
 */
@Repository
@Slf4j
public class TenantJdbcRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Tenant> ROW_MAPPER = (rs, rowNum) -> {
        Tenant t = new Tenant();
        t.setId(rs.getInt("id"));
        t.setName(rs.getString("name"));
        t.setDisplayName(rs.getString("display_name"));
        t.setActive(rs.getBoolean("active"));
        t.setPrimaryColor(rs.getString("primary_color"));
        t.setLogoPath(rs.getString("logo_path"));
        t.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) t.setUpdatedAt(updated.toInstant());
        return t;
    };

    public TenantJdbcRepository(@Qualifier("platformJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Tenant> findAll() {
        return jdbc.query("SELECT * FROM tenants ORDER BY name", ROW_MAPPER);
    }

    public List<Tenant> findAllActive() {
        return jdbc.query("SELECT * FROM tenants WHERE active = true ORDER BY name", ROW_MAPPER);
    }

    public Optional<Tenant> findById(int id) {
        List<Tenant> results = jdbc.query(
                "SELECT * FROM tenants WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<Tenant> findByName(String name) {
        List<Tenant> results = jdbc.query(
                "SELECT * FROM tenants WHERE name = ?", ROW_MAPPER, name);
        return results.stream().findFirst();
    }

    public boolean existsByName(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE name = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public Tenant insert(Tenant tenant) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO tenants (name, display_name, active, primary_color, logo_path) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tenant.getName());
            ps.setString(2, tenant.getDisplayName());
            ps.setBoolean(3, tenant.isActive());
            ps.setString(4, tenant.getPrimaryColor());
            ps.setString(5, tenant.getLogoPath());
            return ps;
        }, keyHolder);
        tenant.setId(Objects.requireNonNull(keyHolder.getKey()).intValue());
        return tenant;
    }

    public void update(Tenant tenant) {
        jdbc.update(
                "UPDATE tenants SET display_name = ?, active = ?, primary_color = ?, logo_path = ?, updated_at = NOW() WHERE id = ?",
                tenant.getDisplayName(), tenant.isActive(),
                tenant.getPrimaryColor(), tenant.getLogoPath(), tenant.getId());
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM tenants WHERE id = ?", id);
    }

    /**
     * Inserta un tenant si no existe (por nombre). Retorna el tenant existente o el nuevo.
     */
    public Tenant insertIfNotExists(String name, String displayName) {
        Optional<Tenant> existing = findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        Tenant tenant = new Tenant(name, displayName);
        return insert(tenant);
    }
}
