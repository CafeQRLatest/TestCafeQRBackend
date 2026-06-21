-- V1_115: Add client_id column to menus table for client-scoped menus
ALTER TABLE menus ADD COLUMN IF NOT EXISTS client_id UUID REFERENCES clients(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_menus_client_id ON menus(client_id);
