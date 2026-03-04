-- V4: Tabla de empleados (versión H2)

CREATE TABLE IF NOT EXISTS employees (
    id          UUID            DEFAULT RANDOM_UUID() PRIMARY KEY,
    first_name  VARCHAR(100)    NOT NULL,
    last_name   VARCHAR(100)    NOT NULL,
    email       VARCHAR(200),
    position    VARCHAR(100),
    department  VARCHAR(100),
    hire_date   DATE,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_employees_last_name ON employees (last_name);
CREATE INDEX IF NOT EXISTS idx_employees_email ON employees (email);
