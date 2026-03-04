-- ============================================================
-- V8__role_schemas.sql
-- Tabla de asignacion de schema_types a roles del tenant.
-- Permite controlar que modulos ve cada rol en el menu.
-- ============================================================

CREATE TABLE IF NOT EXISTS role_schemas (
    role_id     UUID        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    schema_type VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, schema_type)
);

CREATE INDEX IF NOT EXISTS idx_role_schemas_role ON role_schemas(role_id);
