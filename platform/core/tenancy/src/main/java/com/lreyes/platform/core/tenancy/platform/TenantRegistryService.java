package com.lreyes.platform.core.tenancy.platform;

import com.lreyes.platform.core.tenancy.TenantMigrationService;
import com.lreyes.platform.core.tenancy.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicio de registro de tenants: CRUD + creación de schema + migraciones.
 * <p>
 * Mantiene un cache en memoria de tenants activos que se refresca
 * al crear/modificar tenants.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantRegistryService {

    private final TenantJdbcRepository tenantRepo;
    private final TenantSchemaJdbcRepository schemaRepo;
    private final TenantMigrationService tenantMigrationService;

    /** Cache de nombres de tenants activos */
    private final List<String> activeTenantNames = new CopyOnWriteArrayList<>();

    public List<Tenant> findAll() {
        return tenantRepo.findAll();
    }

    public List<Tenant> findAllActive() {
        return tenantRepo.findAllActive();
    }

    public Optional<Tenant> findById(int id) {
        return tenantRepo.findById(id);
    }

    public Optional<Tenant> findByName(String name) {
        return tenantRepo.findByName(name);
    }

    /**
     * Retorna la lista cacheada de nombres de tenants activos.
     */
    public List<String> getActiveTenantNames() {
        if (activeTenantNames.isEmpty()) {
            refreshCache();
        }
        return List.copyOf(activeTenantNames);
    }

    /**
     * Verifica si un tenant está registrado y activo.
     */
    public boolean isRegisteredTenant(String name) {
        return getActiveTenantNames().contains(name);
    }

    /**
     * Crea un nuevo tenant completo:
     * 1. Valida nombre
     * 2. INSERT en tabla tenants
     * 3. INSERT en tenant_schemas con schema_type='core'
     * 4. Crea schema PostgreSQL + Flyway migraciones
     * 5. Refresca cache
     */
    public Tenant createTenant(String name, String displayName) {
        if (!TenantProperties.isValidTenantName(name)) {
            throw new IllegalArgumentException("Nombre de tenant inválido: " + name);
        }
        if (tenantRepo.existsByName(name)) {
            throw new IllegalArgumentException("Ya existe un tenant con nombre: " + name);
        }

        log.info("Creando tenant '{}'", name);

        // 1. Registrar en BD
        Tenant tenant = tenantRepo.insert(new Tenant(name, displayName));

        // 2. Registrar schema core
        schemaRepo.insert(new TenantSchema(tenant.getId(), name, "core"));

        // 3. Crear schema PostgreSQL + migraciones
        tenantMigrationService.ensureSchemaAndMigrate(name);

        // 4. Refrescar cache
        refreshCache();

        log.info("Tenant '{}' creado exitosamente (id={})", name, tenant.getId());
        return tenant;
    }

    /**
     * Actualiza un tenant existente.
     */
    public void updateTenant(int id, String displayName, boolean active,
                             String primaryColor, String logoPath) {
        Tenant tenant = tenantRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + id));
        tenant.setDisplayName(displayName);
        tenant.setActive(active);
        tenant.setPrimaryColor(primaryColor);
        tenant.setLogoPath(logoPath);
        tenantRepo.update(tenant);
        refreshCache();
    }

    /** Schema types que se seedean automáticamente para cada tenant */
    private static final List<String> DEFAULT_SCHEMA_TYPES = List.of("core", "hr", "sales");

    /**
     * Seedea tenants desde la lista YAML si no existen en BD.
     */
    public void seedFromYaml(List<String> yamlTenants) {
        for (String name : yamlTenants) {
            String displayName = name.substring(0, 1).toUpperCase() + name.substring(1);
            Tenant tenant = tenantRepo.insertIfNotExists(name, displayName);
            for (String schemaType : DEFAULT_SCHEMA_TYPES) {
                String schemaName = "core".equals(schemaType) ? name : name + "_" + schemaType;
                schemaRepo.insertIfNotExists(tenant.getId(), schemaName, schemaType);
            }
            log.debug("Tenant '{}' seeded (id={}) with schemas {}", name, tenant.getId(), DEFAULT_SCHEMA_TYPES);
        }
        refreshCache();
    }

    /**
     * Refresca el cache de tenants activos desde BD.
     */
    public void refreshCache() {
        List<String> names = tenantRepo.findAllActive().stream()
                .map(Tenant::getName)
                .toList();
        activeTenantNames.clear();
        activeTenantNames.addAll(names);
        log.debug("Cache de tenants activos refrescado: {}", names);
    }
}
