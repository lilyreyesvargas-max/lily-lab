-- =============================================================================
-- V5: Tablas de ventas (pedidos + líneas)
-- Se aplica dentro del schema de cada tenant.
-- =============================================================================

CREATE TABLE IF NOT EXISTS sales_orders (
    id                   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number         VARCHAR(50)     NOT NULL UNIQUE,
    customer_name        VARCHAR(200)    NOT NULL,
    seller               VARCHAR(100),
    description          VARCHAR(500),
    total_amount         NUMERIC(15,2)   NOT NULL DEFAULT 0,
    status               VARCHAR(30)     NOT NULL DEFAULT 'DRAFT',
    process_instance_id  VARCHAR(64),
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    version              BIGINT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS order_lines (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id  UUID            NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_name    VARCHAR(200)    NOT NULL,
    quantity        INT             NOT NULL DEFAULT 1,
    unit_price      NUMERIC(15,2)   NOT NULL,
    line_total      NUMERIC(15,2)   NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sales_orders_number ON sales_orders (order_number);
CREATE INDEX IF NOT EXISTS idx_sales_orders_status ON sales_orders (status);
CREATE INDEX IF NOT EXISTS idx_order_lines_order_id ON order_lines (sales_order_id);
