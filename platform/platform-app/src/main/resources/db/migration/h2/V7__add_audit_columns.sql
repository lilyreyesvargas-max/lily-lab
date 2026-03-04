-- V7: Agrega created_by / updated_by a roles y catalogs (H2)

ALTER TABLE roles ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE roles ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

ALTER TABLE catalogs ADD COLUMN IF NOT EXISTS created_by VARCHAR(100);
ALTER TABLE catalogs ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);
