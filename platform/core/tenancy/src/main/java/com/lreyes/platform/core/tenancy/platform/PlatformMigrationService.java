package com.lreyes.platform.core.tenancy.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.stereotype.Service;

/**
 * Ejecuta migraciones Flyway para el schema {@code platform}.
 * <p>
 * Usa el DataSource del {@link PlatformJdbcConfig} (schema platform).
 * Las migraciones se buscan en {@code classpath:db/migration/platform}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformMigrationService {

    private final PlatformJdbcConfig platformJdbcConfig;

    /**
     * Ejecuta las migraciones pendientes del schema platform.
     *
     * @return número de migraciones aplicadas
     */
    public int migrate() {
        log.info("=== Migrando schema 'platform' ===");

        Flyway flyway = Flyway.configure()
                .dataSource(platformJdbcConfig.getPlatformDataSource())
                .schemas("platform")
                .locations("classpath:db/migration/platform")
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
}
