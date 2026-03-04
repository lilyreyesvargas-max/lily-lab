package com.lreyes.platform.core.tenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Servicio responsable de crear schemas y ejecutar migraciones Flyway por tenant.
 * <p>
 * Flujo de {@link #ensureSchemaAndMigrate(String)}:
 * <ol>
 *   <li>{@code CREATE SCHEMA IF NOT EXISTS <tenant>}</li>
 *   <li>Instancia Flyway con {@code .schemas(tenant)} → Flyway crea su
 *       {@code flyway_schema_history} dentro del schema del tenant</li>
 *   <li>Ejecuta migraciones pendientes</li>
 *   <li>Registra resultado en logs</li>
 * </ol>
 * <p>
 * Las migraciones se buscan en:
 * <ul>
 *   <li>{@code classpath:db/migration/base} — tablas core (users, roles, etc.)</li>
 *   <li>{@code classpath:db/migration/modules} — tablas de módulos de negocio</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationService {

    private static final String[] PG_MIGRATION_LOCATIONS = {
            "classpath:db/migration/base",
            "classpath:db/migration/modules"
    };

    private static final String[] H2_MIGRATION_LOCATIONS = {
            "classpath:db/migration/h2"
    };

    private final DataSource dataSource;

    /**
     * Asegura que el schema del tenant existe y tiene todas las migraciones aplicadas.
     *
     * @param tenantId identificador del tenant (ya validado por TenantProperties)
     * @return número de migraciones aplicadas en esta ejecución
     */
    public int ensureSchemaAndMigrate(String tenantId) {
        if (!TenantProperties.isValidTenantName(tenantId)) {
            throw new IllegalArgumentException(
                    "Nombre de tenant inválido: " + tenantId);
        }

        log.info("=== Migrando tenant '{}' ===", tenantId);

        createSchemaIfNotExists(tenantId);
        MigrateResult result = runFlyway(tenantId);

        int applied = result.migrationsExecuted;
        if (applied > 0) {
            log.info("Tenant '{}': {} migraciones aplicadas", tenantId, applied);
        } else {
            log.info("Tenant '{}': schema actualizado (sin migraciones pendientes)", tenantId);
        }

        return applied;
    }

    /**
     * Muestra el estado de migraciones de un tenant sin ejecutar nada.
     */
    public MigrationInfo[] getStatus(String tenantId) {
        Flyway flyway = buildFlyway(tenantId);
        return flyway.info().all();
    }

    private void createSchemaIfNotExists(String tenantId) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // En H2, los identificadores sin comillas se convierten a UPPERCASE.
            // Usamos uppercase explícito para consistencia con Flyway.
            String schemaName = resolveSchemaName(conn, tenantId);
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
            log.debug("Schema '{}' verificado/creado", schemaName);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error creando schema para tenant '" + tenantId + "'", e);
        }
    }

    private MigrateResult runFlyway(String tenantId) {
        Flyway flyway = buildFlyway(tenantId);
        return flyway.migrate();
    }

    private Flyway buildFlyway(String tenantId) {
        String schemaName = resolveSchemaNameSafe(tenantId);
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(getMigrationLocations())
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .load();
    }

    /**
     * H2 convierte identificadores sin comillas a UPPERCASE.
     * Para consistencia, usamos UPPERCASE en H2.
     */
    private String resolveSchemaName(Connection conn, String tenantId) throws SQLException {
        String dbProduct = conn.getMetaData().getDatabaseProductName();
        return dbProduct.toLowerCase().contains("h2")
                ? tenantId.toUpperCase()
                : tenantId;
    }

    private String resolveSchemaNameSafe(String tenantId) {
        try (Connection conn = dataSource.getConnection()) {
            return resolveSchemaName(conn, tenantId);
        } catch (SQLException e) {
            return tenantId;
        }
    }

    private String[] getMigrationLocations() {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            if (dbProduct.toLowerCase().contains("h2")) {
                log.debug("Detectado H2: usando migraciones H2-compatibles");
                return H2_MIGRATION_LOCATIONS;
            }
        } catch (SQLException e) {
            log.warn("No se pudo detectar el tipo de BD, usando migraciones PostgreSQL", e);
        }
        return PG_MIGRATION_LOCATIONS;
    }
}
