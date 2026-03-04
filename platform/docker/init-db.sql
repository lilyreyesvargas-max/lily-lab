-- =============================================================================
-- Script de inicialización de PostgreSQL para la plataforma.
-- Se ejecuta automáticamente al crear el contenedor por primera vez
-- (montado en /docker-entrypoint-initdb.d/).
--
-- Crea:
--   1. Schema 'platform' para tablas de Flowable y datos compartidos
--   2. Schemas de tenants configurados (acme, globex)
--   3. Extensiones necesarias
-- =============================================================================

-- Extensión para gen_random_uuid() (PostgreSQL 13+)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Schema para Flowable (motor de workflow) ──
CREATE SCHEMA IF NOT EXISTS platform;
COMMENT ON SCHEMA platform IS 'Schema para tablas de Flowable y datos compartidos de la plataforma';

-- ── Schemas de tenants ──
CREATE SCHEMA IF NOT EXISTS acme;
COMMENT ON SCHEMA acme IS 'Tenant: Acme Corp';

CREATE SCHEMA IF NOT EXISTS globex;
COMMENT ON SCHEMA globex IS 'Tenant: Globex Corporation';

-- ── Verificación ──
DO $$
BEGIN
    RAISE NOTICE '================================================';
    RAISE NOTICE 'Schemas creados: platform, acme, globex';
    RAISE NOTICE 'Base de datos lista para la plataforma.';
    RAISE NOTICE '================================================';
END $$;
