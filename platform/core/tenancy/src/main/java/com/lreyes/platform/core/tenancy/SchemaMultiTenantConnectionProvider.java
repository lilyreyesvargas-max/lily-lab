package com.lreyes.platform.core.tenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Hibernate pide una conexión para un tenant → ejecutamos {@code SET search_path TO <tenant>}.
 * <p>
 * Estrategia:
 * <ul>
 *   <li>{@link #getConnection(String)}: obtiene conexión del pool y cambia el search_path al schema del tenant.</li>
 *   <li>{@link #releaseConnection(String, Connection)}: restaura search_path a "public" antes de devolver al pool.</li>
 * </ul>
 * <p>
 * Seguridad: el nombre del tenant ya fue validado por {@link TenantFilter} con regex
 * (solo alfanumérico + guión bajo), por lo que NO hay riesgo de SQL injection en el SET.
 */
@RequiredArgsConstructor
@Slf4j
public class SchemaMultiTenantConnectionProvider
        implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        try {
            setSchema(connection, tenantIdentifier);
            log.trace("schema → {}", tenantIdentifier);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            resetSchema(connection);
        } finally {
            connection.close();
        }
    }

    private void setSchema(Connection connection, String schema) throws SQLException {
        String dbProduct = connection.getMetaData().getDatabaseProductName();
        boolean isH2 = dbProduct.toLowerCase().contains("h2");
        String resolvedSchema = isH2 ? schema.toUpperCase() : schema;
        String sql = isH2
                ? "SET SCHEMA \"" + resolvedSchema + "\""
                : "SET search_path TO " + resolvedSchema;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Restaura el search_path al default del servidor ("$user", public).
     * Usar RESET en vez de SET search_path TO public evita perder "$user"
     * del path, lo cual causaría que otros componentes (ej. Flowable) no
     * encuentren tablas en el schema del usuario de conexión.
     */
    private void resetSchema(Connection connection) throws SQLException {
        String dbProduct = connection.getMetaData().getDatabaseProductName();
        boolean isH2 = dbProduct.toLowerCase().contains("h2");
        if (isH2) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET SCHEMA \"PUBLIC\"");
            }
        } else {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("RESET search_path");
            }
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("No se puede unwrap a " + unwrapType);
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
    }
}
