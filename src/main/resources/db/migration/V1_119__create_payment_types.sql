-- V1_119: Create payment_types table
CREATE TABLE IF NOT EXISTS payment_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    display_name VARCHAR(120) NOT NULL,
    payment_type VARCHAR(20) NOT NULL DEFAULT 'OTHERS',
    sales CHAR(1) NOT NULL DEFAULT 'Y',
    purchase CHAR(1) NOT NULL DEFAULT 'Y',
    expense CHAR(1) NOT NULL DEFAULT 'Y',
    ledger_ref VARCHAR(80),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    description TEXT,
    isactive CHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_payment_types_client_org
    ON payment_types(client_id, org_id, isactive);
