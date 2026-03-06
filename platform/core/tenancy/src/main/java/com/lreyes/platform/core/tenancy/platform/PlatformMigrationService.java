package com.lreyes.platform.core.tenancy.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Ejecuta migraciones Flyway para el schema {@code platform}.
 * <p>
 * Usa el DataSource del {@link PlatformJdbcConfig} (schema platform).
 * Detecta automáticamente si el motor es H2 para usar scripts compatibles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformMigrationService {

    private static final String PG_MIGRATION_LOCATION = "classpath:db/migration/platform";
    private static final String H2_MIGRATION_LOCATION = "classpath:db/migration/platform-h2";

    private final PlatformJdbcConfig platformJdbcConfig;

    /**
     * Ejecuta las migraciones pendientes del schema platform.
     *
     * @return número de migraciones aplicadas
     */
    public int migrate() {
        log.info("=== Migrando schema 'platform' ===");

        DataSource ds = platformJdbcConfig.getPlatformDataSource();
        boolean h2 = isH2(ds);
        // H2: use H2-compatible scripts (TIMESTAMP vs TIMESTAMPTZ, etc.)
        // H2: schema names stored in uppercase by default
        String location = h2 ? H2_MIGRATION_LOCATION : PG_MIGRATION_LOCATION;
        String schemaName = h2 ? "PLATFORM" : "platform";

        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .schemas(schemaName)
                .locations(location)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .load();

        MigrateResult result = flyway.migrate();
        int applied = result.migrationsExecuted;

        if (applied > 0) {
            log.info("Schema 'platform': {} migraciones aplicadas", applied);
        } else {
            log.info("Schema 'platform': actualizado (sin migraciones pendientes)");
        }

        return applied;
    }

    private boolean isH2(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            return conn.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        } catch (SQLException e) {
            log.warn("No se pudo detectar el tipo de BD para schema platform, usando PostgreSQL", e);
            return false;
        }
    }
}
