-- ============================================================
-- V7__add_audit_columns.sql
-- Agrega created_by / updated_by a roles y catalogs
-- para compatibilidad con BaseEntity.
-- ============================================================

ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

ALTER TABLE catalogs ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE catalogs ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);
