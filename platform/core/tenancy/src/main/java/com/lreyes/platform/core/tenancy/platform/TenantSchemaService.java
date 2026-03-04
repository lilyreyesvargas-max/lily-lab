package com.lreyes.platform.core.tenancy.platform;

import com.lreyes.platform.core.tenancy.TenantMigrationService;
import com.lreyes.platform.core.tenancy.TenantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestión de schemas por tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaService {

    private final TenantJdbcRepository tenantRepo;
    private final TenantSchemaJdbcRepository schemaRepo;
    private final TenantMigrationService tenantMigrationService;

    public List<TenantSchema> findByTenantId(int tenantId) {
        return schemaRepo.findByTenantId(tenantId);
    }

    public Optional<TenantSchema> findById(int id) {
        return schemaRepo.findById(id);
    }

    /**
     * Crea un nuevo schema para un tenant.
     */
    public TenantSchema createSchema(int tenantId, String schemaName, String schemaType) {
        if (!TenantProperties.isValidTenantName(schemaName)) {
            throw new IllegalArgumentException("Nombre de schema inválido: " + schemaName);
        }

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + tenantId));

        if (schemaRepo.existsBySchemaName(schemaName)) {
            throw new IllegalArgumentException("Ya existe un schema con nombre: " + schemaName);
        }

        log.info("Creando schema '{}' (tipo '{}') para tenant '{}'",
                schemaName, schemaType, tenant.getName());

        TenantSchema schema = schemaRepo.insert(
                new TenantSchema(tenantId, schemaName, schemaType));

        // Crear schema PostgreSQL + migraciones
        tenantMigrationService.ensureSchemaAndMigrate(schemaName);

        return schema;
    }

    public void updateSchema(int id, boolean active) {
        TenantSchema schema = schemaRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schema no encontrado: " + id));
        schema.setActive(active);
        schemaRepo.update(schema);
    }
}
