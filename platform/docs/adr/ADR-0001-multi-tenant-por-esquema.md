# ADR-0001: Multi-tenant por esquema en PostgreSQL

## Estado
Aceptada

## Contexto
La plataforma debe soportar múltiples organizaciones (tenants) con aislamiento de datos. Las opciones evaluadas fueron:
1. **Base de datos separada** por tenant
2. **Esquema separado** por tenant (dentro de la misma BD)
3. **Columna discriminadora** (`tenant_id`) en cada tabla

## Decisión
Se adopta **esquema separado** (opción 2) usando `hibernate.multiTenancy=SCHEMA`.

## Razones
- **Aislamiento fuerte** sin la complejidad operacional de múltiples bases de datos.
- **Migraciones centralizadas**: Flyway se ejecuta por schema, garantizando consistencia.
- **Consultas simples**: no se necesita filtro `WHERE tenant_id = ?` en cada query.
- **Escalable**: si un tenant crece mucho, puede migrar a su propia BD sin cambiar código.
- **PostgreSQL nativo**: `SET search_path` es eficiente y bien soportado.

## Consecuencias
- Flyway debe instanciarse por tenant (TenantMigrationService).
- El resolver de tenant debe extraer el ID del JWT claim o header `X-Tenant-Id`.
- Flowable usa un schema compartido (`platform`) separado de los tenants.
- Límite práctico: ~200-500 schemas por instancia PostgreSQL sin degradación.
