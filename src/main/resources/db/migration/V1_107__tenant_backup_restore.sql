-- V1_107: Tenant backup and restore module for Test CafeQR 2.0.

CREATE TABLE IF NOT EXISTS tenant_backup_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_frequency VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    retention_count INTEGER NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_backup_settings_client UNIQUE (client_id),
    CONSTRAINT chk_tenant_backup_settings_frequency CHECK (schedule_frequency IN ('MANUAL', 'DAILY', 'WEEKLY')),
    CONSTRAINT chk_tenant_backup_settings_retention CHECK (retention_count BETWEEN 1 AND 50)
);

CREATE TABLE IF NOT EXISTS tenant_backups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    org_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    requested_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    file_name VARCHAR(255),
    storage_path TEXT,
    size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    row_count BIGINT,
    manifest_json JSONB,
    error_message TEXT,
    restore_token VARCHAR(128),
    restore_token_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    CONSTRAINT chk_tenant_backups_type CHECK (type IN ('MANUAL', 'SCHEDULED', 'PRE_RESTORE', 'RESTORE_UPLOAD')),
    CONSTRAINT chk_tenant_backups_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'PREVIEWED', 'RESTORED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_backups_client_created
    ON tenant_backups(client_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_backups_restore_token
    ON tenant_backups(restore_token)
    WHERE restore_token IS NOT NULL;

INSERT INTO menus (id, name, url, description, parent_id, isactive, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'Data Backup',
    '/owner/data-backup',
    'Download and restore restaurant backup files.',
    NULL,
    'Y',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM menus WHERE url = '/owner/data-backup'
);

INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r
CROSS JOIN menus m
WHERE m.url = '/owner/data-backup'
  AND r.name IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
