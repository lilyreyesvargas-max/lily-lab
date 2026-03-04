-- V1: Tablas base del core (versión H2 para desarrollo)

CREATE TABLE IF NOT EXISTS users (
    id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    username        VARCHAR(100)  NOT NULL UNIQUE,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    full_name       VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(255),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS roles (
    id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name        VARCHAR(50)   NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    entity_type VARCHAR(100)  NOT NULL,
    entity_id   UUID,
    action      VARCHAR(50)   NOT NULL,
    username    VARCHAR(100),
    timestamp   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    details     TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp
    ON audit_logs(timestamp);

CREATE TABLE IF NOT EXISTS catalogs (
    id          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    type        VARCHAR(100)  NOT NULL,
    code        VARCHAR(100)  NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order  INT           DEFAULT 0,
    parent_id   UUID          REFERENCES catalogs(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT        NOT NULL DEFAULT 0,
    UNIQUE(type, code)
);

CREATE INDEX IF NOT EXISTS idx_catalogs_type ON catalogs(type);

CREATE TABLE IF NOT EXISTS files_meta (
    id            UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    storage_path  VARCHAR(500),
    entity_type   VARCHAR(100),
    entity_id     UUID,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_files_meta_entity
    ON files_meta(entity_type, entity_id);

INSERT INTO roles (id, name, description) VALUES
    (RANDOM_UUID(), 'admin',    'Administrador del sistema'),
    (RANDOM_UUID(), 'gestor',   'Gestor de operaciones'),
    (RANDOM_UUID(), 'operador', 'Operador estándar'),
    (RANDOM_UUID(), 'auditor',  'Solo lectura y auditoría');
