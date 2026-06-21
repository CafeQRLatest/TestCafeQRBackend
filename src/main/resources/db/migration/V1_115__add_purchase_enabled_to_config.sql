-- Migration to add purchase_enabled column to system_configurations table
ALTER TABLE system_configurations ADD COLUMN IF NOT EXISTS purchase_enabled BOOLEAN DEFAULT TRUE;
UPDATE system_configurations SET purchase_enabled = TRUE WHERE purchase_enabled IS NULL;
