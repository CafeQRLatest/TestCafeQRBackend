-- Scope system configuration rows by super-admin tenant/client.
-- The legacy null-client row remains only as a global fallback/default row.

ALTER TABLE system_configurations
ADD COLUMN IF NOT EXISTS client_id UUID,
ADD COLUMN IF NOT EXISTS org_id UUID;

CREATE INDEX IF NOT EXISTS idx_system_configurations_client_id
ON system_configurations (client_id);

CREATE INDEX IF NOT EXISTS idx_system_configurations_client_org
ON system_configurations (client_id, org_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_configurations_client_default
ON system_configurations (client_id)
WHERE client_id IS NOT NULL AND org_id IS NULL;
