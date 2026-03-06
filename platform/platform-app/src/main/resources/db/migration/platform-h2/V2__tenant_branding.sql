-- ============================================================
-- V2__tenant_branding.sql
-- Campos de branding por tenant: color corporativo y logotipo.
-- ============================================================

ALTER TABLE tenants ADD COLUMN primary_color VARCHAR(7);
ALTER TABLE tenants ADD COLUMN logo_path VARCHAR(500);
