-- =============================================
-- V1_86: Standalone Expenses Migration
-- =============================================

-- 1. Create expenses table
CREATE TABLE IF NOT EXISTS expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID REFERENCES clients(id),
    org_id          UUID REFERENCES organizations(id),
    terminal_id     UUID REFERENCES terminals(id),
    expense_no      VARCHAR(50) NOT NULL,
    category_id     UUID REFERENCES expense_categories(id),
    expense_date    TIMESTAMP NOT NULL DEFAULT NOW(),
    amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    description     TEXT,
    payment_method  VARCHAR(50),
    status          VARCHAR(20) DEFAULT 'COMPLETED',
    doc_status      VARCHAR(20) DEFAULT 'COMPLETED',
    payment_status  VARCHAR(20) DEFAULT 'PAID',
    original_expense_id UUID,
    revision_number INT DEFAULT 0,
    currency_id     UUID REFERENCES currencies(id),
    isactive        CHAR(1) DEFAULT 'Y',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

-- 2. Create indices for performance and uniqueness
CREATE INDEX IF NOT EXISTS idx_expenses_client     ON expenses(client_id);
CREATE INDEX IF NOT EXISTS idx_expenses_org        ON expenses(org_id);
CREATE INDEX IF NOT EXISTS idx_expenses_date       ON expenses(expense_date);
CREATE INDEX IF NOT EXISTS idx_expenses_category   ON expenses(category_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_expenses_client_org_expense_no 
    ON expenses (client_id, org_id, expense_no)
    WHERE client_id IS NOT NULL AND org_id IS NOT NULL AND expense_no IS NOT NULL;

-- 3. Add expense_id to invoices and payments tables
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS expense_id UUID REFERENCES expenses(id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS expense_id UUID REFERENCES expenses(id);

-- 4. Copy legacy expenses from orders table
INSERT INTO expenses (
    id, client_id, org_id, terminal_id, expense_no, category_id, expense_date, amount,
    description, payment_method, status, doc_status, payment_status,
    original_expense_id, revision_number, currency_id, isactive,
    created_at, updated_at, created_by, updated_by
)
SELECT 
    id, client_id, org_id, terminal_id, order_no, expense_category_id, order_date, grand_total,
    description, COALESCE(reference, (SELECT p.payment_method FROM payments p WHERE p.order_id = orders.id LIMIT 1), 'CASH'),
    order_status, doc_status, payment_status,
    original_order_id, revision_number, currency_id, isactive,
    created_at, updated_at, 
    CAST(created_by AS VARCHAR), CAST(updated_by AS VARCHAR)
FROM orders
WHERE order_type = 'EXPENSE';

-- 5. Backfill invoices and payments to reference expenses(id)
UPDATE invoices
SET expense_id = order_id
WHERE order_id IN (SELECT id FROM expenses);

UPDATE payments
SET expense_id = order_id
WHERE order_id IN (SELECT id FROM expenses);

-- 6. Decouple invoices and payments from orders
UPDATE invoices
SET order_id = NULL
WHERE expense_id IS NOT NULL;

UPDATE payments
SET order_id = NULL
WHERE expense_id IS NOT NULL;

-- 7. Delete legacy expense orders from orders table (and order lines if any exist)
DELETE FROM order_lines
WHERE order_id IN (SELECT id FROM expenses);

DELETE FROM orders
WHERE order_type = 'EXPENSE';
