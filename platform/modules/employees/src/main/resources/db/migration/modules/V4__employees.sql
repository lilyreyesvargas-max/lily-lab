-- =============================================================================
-- V4: Tabla de empleados
-- Se aplica dentro del schema de cada tenant.
-- =============================================================================

CREATE TABLE IF NOT EXISTS employees (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(100)    NOT NULL,
    last_name   VARCHAR(100)    NOT NULL,
    email       VARCHAR(200),
    position    VARCHAR(100),
    department  VARCHAR(100),
    hire_date   DATE,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_employees_last_name ON employees (last_name);
CREATE INDEX IF NOT EXISTS idx_employees_email ON employees (email);
