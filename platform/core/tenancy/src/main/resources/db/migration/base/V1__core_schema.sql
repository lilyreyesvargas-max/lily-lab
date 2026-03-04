-- ============================================================
-- V1__core_schema.sql
-- Tablas base del core aplicadas DENTRO del schema del tenant.
-- Cada tenant (acme, globex, ...) tendrá su propia copia.
-- ============================================================

-- ========================
-- USERS
-- ========================
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(100)  NOT NULL UNIQUE,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    full_name       VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(255),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT        NOT NULL DEFAULT 0
);

-- ========================
-- ROLES
-- ========================
CREATE TABLE IF NOT EXISTS roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)   NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    version     BIGINT        NOT NULL DEFAULT 0
);

-- ========================
-- USER ↔ ROLES (N:M)
-- ========================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- ========================
-- AUDIT LOGS
-- ========================
CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100)  NOT NULL,
    entity_id   UUID,
    action      VARCHAR(50)   NOT NULL,
    username    VARCHAR(100),
    timestamp   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(45),
    details     TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp
    ON audit_logs(timestamp);

-- ========================
-- CATALOGS (nomencladores)
-- ========================
CREATE TABLE IF NOT EXISTS catalogs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(100)  NOT NULL,
    code        VARCHAR(100)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order  INT           DEFAULT 0,
    parent_id   UUID          REFERENCES catalogs(id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    version     BIGINT        NOT NULL DEFAULT 0,
    UNIQUE(type, code)
);

CREATE INDEX IF NOT EXISTS idx_catalogs_type ON catalogs(type);

-- ========================
-- FILES META
-- ========================
CREATE TABLE IF NOT EXISTS files_meta (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    storage_path  VARCHAR(500),
    entity_type   VARCHAR(100),
    entity_id     UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_files_meta_entity
    ON files_meta(entity_type, entity_id);

-- ========================
-- SEED: roles base
-- ========================
INSERT INTO roles (id, name, description) VALUES
    (gen_random_uuid(), 'admin',    'Administrador del sistema'),
    (gen_random_uuid(), 'gestor',   'Gestor de operaciones'),
    (gen_random_uuid(), 'operador', 'Operador estándar'),
    (gen_random_uuid(), 'auditor',  'Solo lectura y auditoría')
ON CONFLICT (name) DO NOTHING;
