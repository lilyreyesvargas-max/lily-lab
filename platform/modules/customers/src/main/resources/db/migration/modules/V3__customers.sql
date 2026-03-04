-- =============================================================================
-- V3: Tabla de clientes
-- Se aplica dentro del schema de cada tenant.
-- =============================================================================

CREATE TABLE IF NOT EXISTS customers (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200)    NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(50),
    address     VARCHAR(500),
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_customers_name ON customers (name);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers (email);
