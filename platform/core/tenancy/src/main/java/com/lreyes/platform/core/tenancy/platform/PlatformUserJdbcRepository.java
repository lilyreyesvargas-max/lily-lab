package com.lreyes.platform.core.tenancy.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JDBC para la tabla {@code platform.platform_users}.
 */
@Repository
@Slf4j
public class PlatformUserJdbcRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<PlatformUser> ROW_MAPPER = (rs, rowNum) -> {
        PlatformUser u = new PlatformUser();
        u.setId(rs.getObject("id", UUID.class));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setEmail(rs.getString("email"));
        u.setFullName(rs.getString("full_name"));
        u.setEnabled(rs.getBoolean("enabled"));
        u.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) u.setUpdatedAt(updated.toInstant());
        return u;
    };

    public PlatformUserJdbcRepository(@Qualifier("platformJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlatformUser> findAll() {
        return jdbc.query("SELECT * FROM platform_users ORDER BY username", ROW_MAPPER);
    }

    public Optional<PlatformUser> findById(UUID id) {
        List<PlatformUser> results = jdbc.query(
                "SELECT * FROM platform_users WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<PlatformUser> findByUsername(String username) {
        List<PlatformUser> results = jdbc.query(
                "SELECT * FROM platform_users WHERE username = ?", ROW_MAPPER, username);
        return results.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_users WHERE username = ?",
                Integer.class, username);
        return count != null && count > 0;
    }

    public PlatformUser insert(PlatformUser user) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO platform_users (id, username, password_hash, email, full_name, enabled) VALUES (?, ?, ?, ?, ?, ?)",
                id, user.getUsername(), user.getPasswordHash(),
                user.getEmail(), user.getFullName(), user.isEnabled());
        user.setId(id);
        return user;
    }

    public void update(PlatformUser user) {
        jdbc.update(
                "UPDATE platform_users SET email = ?, full_name = ?, enabled = ?, "
                        + "updated_at = NOW() WHERE id = ?",
                user.getEmail(), user.getFullName(), user.isEnabled(), user.getId());
    }

    public void updatePassword(UUID id, String passwordHash) {
        jdbc.update(
                "UPDATE platform_users SET password_hash = ?, updated_at = NOW() WHERE id = ?",
                passwordHash, id);
    }

    public void delete(UUID id) {
        jdbc.update("DELETE FROM platform_users WHERE id = ?", id);
    }
}
