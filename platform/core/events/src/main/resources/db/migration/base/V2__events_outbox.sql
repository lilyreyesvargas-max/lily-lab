-- ============================================================
-- V2__events_outbox.sql
-- Tabla outbox para garantizar entrega de eventos de dominio.
-- Se aplica DENTRO del schema del tenant (igual que V1).
-- ============================================================

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(200)  NOT NULL,
    aggregate_type  VARCHAR(200)  NOT NULL,
    aggregate_id    UUID,
    tenant_id       VARCHAR(63)   NOT NULL,
    payload         JSONB         NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT           NOT NULL DEFAULT 0,
    last_error      TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_events(status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);
