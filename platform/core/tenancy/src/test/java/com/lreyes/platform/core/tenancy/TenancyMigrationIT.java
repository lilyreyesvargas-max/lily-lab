package com.lreyes.platform.core.tenancy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración con Testcontainers (PostgreSQL real).
 * <p>
 * Valida:
 * <ul>
 *   <li>Creación de schemas {@code acme} y {@code globex}</li>
 *   <li>Existencia de tablas core ({@code users}, {@code roles}, {@code audit_logs}) en ambos schemas</li>
 *   <li>Aislamiento: datos insertados en un schema NO aparecen en otro</li>
 *   <li>Roles seed insertados en ambos schemas</li>
 * </ul>
 * <p>
 * REQUIERE Docker instalado. Si Docker no está disponible, este test se saltará automáticamente.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenancyMigrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("platform")
                    .withUsername("platform")
                    .withPassword("platform");

    static DataSource dataSource;
    static TenantMigrationService migrationService;

    @BeforeAll
    static void setUp() {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        migrationService = new TenantMigrationService(dataSource);
    }

    @Test
    @Order(1)
    void shouldMigrateAcmeSchema() {
        int applied = migrationService.ensureSchemaAndMigrate("acme");
        assertTrue(applied > 0, "Debería aplicar al menos 1 migración para acme");
    }

    @Test
    @Order(2)
    void shouldMigrateGlobexSchema() {
        int applied = migrationService.ensureSchemaAndMigrate("globex");
        assertTrue(applied > 0, "Debería aplicar al menos 1 migración para globex");
    }

    @Test
    @Order(3)
    void shouldBeIdempotent() {
        // Segunda ejecución no debería aplicar migraciones nuevas
        int applied = migrationService.ensureSchemaAndMigrate("acme");
        assertEquals(0, applied, "No debería haber migraciones pendientes");
    }

    @Test
    @Order(4)
    void acmeSchema_shouldHaveCoreTables() {
        assertTablesExistInSchema("acme",
                List.of("users", "roles", "user_roles", "audit_logs", "catalogs", "files_meta"));
    }

    @Test
    @Order(5)
    void globexSchema_shouldHaveCoreTables() {
        assertTablesExistInSchema("globex",
                List.of("users", "roles", "user_roles", "audit_logs", "catalogs", "files_meta"));
    }

    @Test
    @Order(6)
    void bothSchemas_shouldHaveFlywayHistory() {
        assertTablesExistInSchema("acme", List.of("flyway_schema_history"));
        assertTablesExistInSchema("globex", List.of("flyway_schema_history"));
    }

    @Test
    @Order(7)
    void bothSchemas_shouldHaveSeededRoles() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer acmeRoles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM acme.roles", Integer.class);
        Integer globexRoles = jdbc.queryForObject(
                "SELECT COUNT(*) FROM globex.roles", Integer.class);

        assertEquals(4, acmeRoles, "acme debería tener 4 roles (admin, gestor, operador, auditor)");
        assertEquals(4, globexRoles, "globex debería tener 4 roles");
    }

    @Test
    @Order(8)
    void schemas_shouldBeIsolated() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Insertar un usuario en acme
        jdbc.execute("""
                INSERT INTO acme.users (username, email, full_name)
                VALUES ('user_acme', 'user@acme.com', 'Acme User')
                """);

        // Verificar que el usuario existe en acme
        Integer acmeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM acme.users WHERE username = 'user_acme'",
                Integer.class);
        assertEquals(1, acmeCount);

        // Verificar que NO existe en globex (aislamiento)
        Integer globexCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM globex.users WHERE username = 'user_acme'",
                Integer.class);
        assertEquals(0, globexCount, "El usuario de acme NO debe aparecer en globex");
    }

    @Test
    @Order(9)
    void invalidTenant_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> migrationService.ensureSchemaAndMigrate("DROP SCHEMA public"));
    }

    // ── Helper ──────────────────────────────────────────────

    private void assertTablesExistInSchema(String schema, List<String> expectedTables) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        for (String table : expectedTables) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = ?
                    """, Integer.class, schema, table);

            assertNotNull(count);
            assertTrue(count > 0,
                    String.format("Tabla '%s' no encontrada en schema '%s'", table, schema));
        }
    }
}
