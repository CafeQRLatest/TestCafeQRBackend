-- V1_80: Full accrual accounting integration, default mappings and posting safety.

ALTER TABLE accounting_accounts
    ADD COLUMN IF NOT EXISTS system_key VARCHAR(80);

ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS reversal_of_journal_entry_id UUID REFERENCES journal_entries(id),
    ADD COLUMN IF NOT EXISTS auto_posted BOOLEAN DEFAULT FALSE;

ALTER TABLE expense_categories
    ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES accounting_accounts(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_accounts_system_key
    ON accounting_accounts(client_id, COALESCE(org_id, '00000000-0000-0000-0000-000000000000'::uuid), system_key)
    WHERE system_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_journal_entries_source
    ON journal_entries(client_id, COALESCE(org_id, '00000000-0000-0000-0000-000000000000'::uuid), source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL AND status <> 'VOID';

CREATE INDEX IF NOT EXISTS idx_journal_entries_reversal
    ON journal_entries(reversal_of_journal_entry_id);

CREATE TABLE IF NOT EXISTS accounting_account_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    mapping_key VARCHAR(80) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounting_accounts(id),
    description TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_accounting_account_mappings_key UNIQUE (client_id, org_id, mapping_key)
);

CREATE TABLE IF NOT EXISTS accounting_payment_method_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    payment_method VARCHAR(50) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounting_accounts(id),
    description TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_accounting_payment_method_mappings_key UNIQUE (client_id, org_id, payment_method)
);

CREATE TABLE IF NOT EXISTS payment_splits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    payment_method VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    reference_no VARCHAR(120),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_payment_splits_payment
    ON payment_splits(client_id, payment_id);

CREATE TABLE IF NOT EXISTS accounting_posting_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    source_type VARCHAR(80) NOT NULL,
    source_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    posted_journal_entry_id UUID REFERENCES journal_entries(id),
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    posted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_accounting_posting_jobs_source UNIQUE (client_id, org_id, source_type, source_id)
);

CREATE INDEX IF NOT EXISTS idx_accounting_posting_jobs_status
    ON accounting_posting_jobs(client_id, org_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_expense_categories_account
    ON expense_categories(account_id);
