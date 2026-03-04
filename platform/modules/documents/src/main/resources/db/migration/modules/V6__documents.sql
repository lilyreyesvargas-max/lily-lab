-- =============================================================================
-- V6: Tabla de documentos (DMS metadatos)
-- Se aplica dentro del schema de cada tenant.
-- =============================================================================

CREATE TABLE IF NOT EXISTS documents (
    id                   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title                VARCHAR(300)    NOT NULL,
    description          VARCHAR(1000),
    file_name            VARCHAR(300)    NOT NULL,
    content_type         VARCHAR(100),
    file_size            BIGINT,
    storage_path         VARCHAR(500),
    doc_version          INT             NOT NULL DEFAULT 1,
    related_entity_type  VARCHAR(100),
    related_entity_id    VARCHAR(100),
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ,
    created_by           VARCHAR(100),
    updated_by           VARCHAR(100),
    version              BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_documents_title ON documents (title);
CREATE INDEX IF NOT EXISTS idx_documents_related ON documents (related_entity_type, related_entity_id);
