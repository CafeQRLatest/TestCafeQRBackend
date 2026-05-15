-- V1_79: ERP accounting foundation that reuses the existing client/org/terminal/warehouse structure.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS document_kind VARCHAR(50);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS document_kind VARCHAR(50);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS document_kind VARCHAR(50);

UPDATE orders
SET document_kind = CASE
    WHEN order_type = 'SALE' THEN 'SALE_ORDER'
    WHEN order_type = 'PURCHASE' THEN 'PURCHASE_ORDER'
    WHEN order_type = 'EXPENSE' THEN 'EXPENSE'
    ELSE document_kind
END
WHERE document_kind IS NULL;

UPDATE invoices
SET document_kind = CASE
    WHEN invoice_type = 'CUSTOMER_INVOICE' THEN 'CUSTOMER_INVOICE'
    WHEN invoice_type = 'VENDOR_BILL' THEN 'VENDOR_BILL'
    WHEN invoice_type = 'EXPENSE_RECEIPT' THEN 'EXPENSE_RECEIPT'
    ELSE document_kind
END
WHERE document_kind IS NULL;

UPDATE payments
SET document_kind = CASE
    WHEN payment_type = 'INBOUND' THEN 'PAYMENT_IN'
    WHEN payment_type = 'OUTBOUND' THEN 'PAYMENT_OUT'
    ELSE document_kind
END
WHERE document_kind IS NULL;

CREATE TABLE IF NOT EXISTS accounting_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    account_sub_type VARCHAR(60),
    currency_id UUID,
    opening_balance NUMERIC(15, 2) DEFAULT 0,
    current_balance NUMERIC(15, 2) DEFAULT 0,
    is_system_account BOOLEAN DEFAULT FALSE,
    is_cash_account BOOLEAN DEFAULT FALSE,
    is_bank_account BOOLEAN DEFAULT FALSE,
    bank_name VARCHAR(160),
    account_number VARCHAR(100),
    ifsc_code VARCHAR(40),
    upi_id VARCHAR(120),
    description TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_accounting_accounts_client_org_code UNIQUE (client_id, org_id, code)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    entry_no VARCHAR(80) NOT NULL,
    entry_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    source_type VARCHAR(50),
    source_id UUID,
    terminal_id UUID,
    warehouse_id UUID,
    currency_id UUID,
    total_debit NUMERIC(15, 2) DEFAULT 0,
    total_credit NUMERIC(15, 2) DEFAULT 0,
    description TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS journal_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounting_accounts(id),
    party_type VARCHAR(20),
    party_id UUID,
    debit NUMERIC(15, 2) DEFAULT 0,
    credit NUMERIC(15, 2) DEFAULT 0,
    description TEXT
);

CREATE TABLE IF NOT EXISTS party_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    party_type VARCHAR(20) NOT NULL,
    party_id UUID NOT NULL,
    account_id UUID REFERENCES accounting_accounts(id),
    journal_entry_id UUID REFERENCES journal_entries(id),
    source_type VARCHAR(50),
    source_id UUID,
    entry_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    debit NUMERIC(15, 2) DEFAULT 0,
    credit NUMERIC(15, 2) DEFAULT 0,
    balance_after NUMERIC(15, 2) DEFAULT 0,
    description TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS payment_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id),
    org_id UUID REFERENCES organizations(id),
    payment_id UUID NOT NULL,
    invoice_id UUID,
    order_id UUID,
    allocated_amount NUMERIC(15, 2) NOT NULL,
    allocation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'POSTED',
    notes TEXT,
    isactive CHAR(1) DEFAULT 'Y',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_accounting_accounts_scope ON accounting_accounts(client_id, org_id, isactive);
CREATE INDEX IF NOT EXISTS idx_journal_entries_scope_date ON journal_entries(client_id, org_id, entry_date DESC);
CREATE INDEX IF NOT EXISTS idx_journal_lines_entry ON journal_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_journal_lines_account ON journal_lines(account_id);
CREATE INDEX IF NOT EXISTS idx_party_ledger_scope_party ON party_ledger_entries(client_id, org_id, party_type, party_id, entry_date DESC);
CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment ON payment_allocations(client_id, payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocations_invoice ON payment_allocations(client_id, invoice_id);

INSERT INTO permissions (id, name, description, created_at, updated_at)
SELECT gen_random_uuid(), permission_name, permission_description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('VIEW_ACCOUNTING', 'Allow viewing chart of accounts, journals, ledgers and accounting reports'),
    ('MANAGE_ACCOUNTING', 'Allow creating and updating chart of accounts'),
    ('POST_JOURNAL', 'Allow posting accounting journals'),
    ('ALLOCATE_PAYMENT', 'Allow allocating payments against invoices and orders')
) AS p(permission_name, permission_description)
WHERE NOT EXISTS (
    SELECT 1 FROM permissions existing WHERE existing.name = p.permission_name
);

INSERT INTO menus (id, name, url, description, parent_id, isactive, created_at, updated_at)
SELECT gen_random_uuid(), 'Accounting', '/owner/accounting', 'Chart of Accounts, Journals & Ledgers', NULL, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM menus WHERE url = '/owner/accounting'
);

INSERT INTO role_menus (role_id, menu_id)
SELECT r.id, m.id
FROM roles r
CROSS JOIN menus m
WHERE m.url = '/owner/accounting'
  AND r.name IN ('SUPER_ADMIN', 'ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
  AND p.name IN ('VIEW_ACCOUNTING', 'MANAGE_ACCOUNTING', 'POST_JOURNAL', 'ALLOCATE_PAYMENT')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('VIEW_ACCOUNTING', 'POST_JOURNAL', 'ALLOCATE_PAYMENT')
ON CONFLICT DO NOTHING;
