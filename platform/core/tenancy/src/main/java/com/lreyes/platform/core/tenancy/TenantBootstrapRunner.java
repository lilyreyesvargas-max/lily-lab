package com.lreyes.platform.core.tenancy;

import com.lreyes.platform.core.tenancy.platform.PlatformMigrationService;
import com.lreyes.platform.core.tenancy.platform.PlatformUserService;
import com.lreyes.platform.core.tenancy.platform.TenantRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Se ejecuta al iniciar la aplicación.
 * <p>
 * Flujo:
 * <ol>
 *   <li>Migrar schema {@code platform} (tablas de administración global)</li>
 *   <li>Seedear platform admin por defecto</li>
 *   <li>Seedear tenants YAML → BD (si no existen)</li>
 *   <li>Leer tenants activos desde BD</li>
 *   <li>Ejecutar migraciones Flyway por cada tenant activo</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantBootstrapRunner implements ApplicationRunner {

    private final TenantProperties tenantProperties;
    private final TenantMigrationService migrationService;
    private final PlatformMigrationService platformMigrationService;
    private final TenantRegistryService tenantRegistryService;
    private final PlatformUserService platformUserService;

    @Override
    public void run(ApplicationArguments args) {
        // 1. Migrar schema 'platform' (tablas globales)
        log.info("=== Bootstrap: migrando schema 'platform' ===");
        platformMigrationService.migrate();

        // 2. Seedear platform admin por defecto
        platformUserService.seedDefaultAdmin();

        // 3. Seedear tenants YAML → BD
        List<String> yamlTenants = tenantProperties.getTenants();
        if (!yamlTenants.isEmpty()) {
            log.info("Seeding {} tenant(s) desde YAML: {}", yamlTenants.size(), yamlTenants);
            tenantRegistryService.seedFromYaml(yamlTenants);
        }

        // 4. Leer tenants activos desde BD
        List<String> activeTenants = tenantRegistryService.getActiveTenantNames();
        if (activeTenants.isEmpty()) {
            log.warn("No hay tenants activos en BD. "
                    + "La aplicación arrancará sin schemas de tenant.");
            return;
        }

        log.info("Iniciando migración de {} tenant(s) activos: {}", activeTenants.size(), activeTenants);

        // 5. Migrar cada tenant activo
        int totalMigrations = 0;
        for (String tenant : activeTenants) {
            try {
                int applied = migrationService.ensureSchemaAndMigrate(tenant);
                totalMigrations += applied;
            } catch (Exception e) {
                log.error("Error migrando tenant '{}': {}", tenant, e.getMessage(), e);
                throw new RuntimeException(
                        "Fallo en migración del tenant '" + tenant + "'. "
                                + "La aplicación no puede arrancar de forma segura.", e);
            }
        }

        log.info("Bootstrap completo: {} tenant(s) migrados, {} migraciones totales aplicadas",
                activeTenants.size(), totalMigrations);
    }
}
