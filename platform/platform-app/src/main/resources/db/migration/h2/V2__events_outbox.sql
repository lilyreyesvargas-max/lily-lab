-- V2: Tabla outbox para eventos de dominio (versión H2)

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    event_type      VARCHAR(200)  NOT NULL,
    aggregate_type  VARCHAR(200)  NOT NULL,
    aggregate_id    UUID,
    tenant_id       VARCHAR(63)   NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    retry_count     INT           NOT NULL DEFAULT 0,
    last_error      TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_events(status, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);
