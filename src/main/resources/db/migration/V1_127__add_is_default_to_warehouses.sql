-- Add is_default column to warehouses table
-- Allows marking one warehouse per org as the default stock deduction point for sales
ALTER TABLE warehouses ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- Create partial index: only one default warehouse per client+org combo
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouses_default_per_org
    ON warehouses (client_id, org_id)
    WHERE is_default = TRUE AND org_id IS NOT NULL;

-- Allow at most one global default per client (when org_id is null)
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouses_default_global
    ON warehouses (client_id)
    WHERE is_default = TRUE AND org_id IS NULL;
