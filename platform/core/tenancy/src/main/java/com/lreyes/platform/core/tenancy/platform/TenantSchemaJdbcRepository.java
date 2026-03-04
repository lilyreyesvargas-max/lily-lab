package com.lreyes.platform.core.tenancy.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JDBC para la tabla {@code platform.tenant_schemas}.
 */
@Repository
@Slf4j
public class TenantSchemaJdbcRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<TenantSchema> ROW_MAPPER = (rs, rowNum) -> {
        TenantSchema ts = new TenantSchema();
        ts.setId(rs.getInt("id"));
        ts.setTenantId(rs.getInt("tenant_id"));
        ts.setSchemaName(rs.getString("schema_name"));
        ts.setSchemaType(rs.getString("schema_type"));
        ts.setActive(rs.getBoolean("active"));
        ts.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        return ts;
    };

    public TenantSchemaJdbcRepository(@Qualifier("platformJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TenantSchema> findByTenantId(int tenantId) {
        return jdbc.query(
                "SELECT * FROM tenant_schemas WHERE tenant_id = ? ORDER BY schema_type",
                ROW_MAPPER, tenantId);
    }

    public Optional<TenantSchema> findById(int id) {
        List<TenantSchema> results = jdbc.query(
                "SELECT * FROM tenant_schemas WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<TenantSchema> findByTenantIdAndType(int tenantId, String schemaType) {
        List<TenantSchema> results = jdbc.query(
                "SELECT * FROM tenant_schemas WHERE tenant_id = ? AND schema_type = ?",
                ROW_MAPPER, tenantId, schemaType);
        return results.stream().findFirst();
    }

    public boolean existsBySchemaName(String schemaName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_schemas WHERE schema_name = ?",
                Integer.class, schemaName);
        return count != null && count > 0;
    }

    public TenantSchema insert(TenantSchema schema) {
        Integer id = jdbc.queryForObject(
                "INSERT INTO tenant_schemas (tenant_id, schema_name, schema_type, active) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                Integer.class,
                schema.getTenantId(), schema.getSchemaName(),
                schema.getSchemaType(), schema.isActive());
        schema.setId(id);
        return schema;
    }

    public void update(TenantSchema schema) {
        jdbc.update(
                "UPDATE tenant_schemas SET active = ? WHERE id = ?",
                schema.isActive(), schema.getId());
    }

    public void delete(int id) {
        jdbc.update("DELETE FROM tenant_schemas WHERE id = ?", id);
    }

    /**
     * Inserta si no existe (por tenant_id + schema_type).
     */
    public TenantSchema insertIfNotExists(int tenantId, String schemaName, String schemaType) {
        Optional<TenantSchema> existing = findByTenantIdAndType(tenantId, schemaType);
        if (existing.isPresent()) {
            return existing.get();
        }
        TenantSchema schema = new TenantSchema(tenantId, schemaName, schemaType);
        return insert(schema);
    }
}
