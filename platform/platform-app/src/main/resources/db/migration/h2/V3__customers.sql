-- V3: Tabla de clientes (versión H2)

CREATE TABLE IF NOT EXISTS customers (
    id          UUID            DEFAULT RANDOM_UUID() PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(50),
    address     VARCHAR(500),
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    version     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_customers_name ON customers (name);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers (email);
