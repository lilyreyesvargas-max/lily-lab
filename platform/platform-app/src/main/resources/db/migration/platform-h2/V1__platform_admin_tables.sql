-- ============================================================
-- V1__platform_admin_tables.sql (H2-compatible)
-- Same as PostgreSQL version but with TIMESTAMP instead of TIMESTAMPTZ.
-- ============================================================

CREATE TABLE IF NOT EXISTS tenants (
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(63) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tenant_schemas (
    id          SERIAL PRIMARY KEY,
    tenant_id   INT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    schema_name VARCHAR(100) NOT NULL UNIQUE,
    schema_type VARCHAR(50) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, schema_type)
);

CREATE TABLE IF NOT EXISTS platform_users (
    id            UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    full_name     VARCHAR(255) NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP
);
