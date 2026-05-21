-- ═══════════════════════════════════════════════════════════════════════════════
-- V1_85: Backfill org_id for legacy records created before multi-branch support
-- ═══════════════════════════════════════════════════════════════════════════════
-- 
-- BACKGROUND:
-- Orders, invoices, payments, and related records created before multi-branch 
-- support have org_id = NULL. These records are invisible to branch-scoped 
-- accounting queries and cannot generate journal entries because the accounting 
-- system accounts only exist under specific branch org_ids.
--
-- STRATEGY:
-- For each client, we find the EARLIEST created organization (the "primary" branch)
-- and assign all NULL org_id records to that organization. This ensures:
--   1. Legacy orders appear when filtering by the primary branch
--   2. "Sync Selected Period" can generate journal entries for them
--   3. All financial data (Sales Summary, Accounting, P&L) is consistent
--
-- SAFETY:
-- - Only updates records WHERE org_id IS NULL (never overwrites existing values)
-- - Uses the earliest-created organization per client as the default
-- - Idempotent: safe to re-run
-- ═══════════════════════════════════════════════════════════════════════════════

-- 1. Orders (the core financial document)
UPDATE orders o
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE o.client_id = sub.client_id AND o.org_id IS NULL;

-- 2. Invoices
UPDATE invoices i
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE i.client_id = sub.client_id AND i.org_id IS NULL;

-- 3. Payments
UPDATE payments p
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE p.client_id = sub.client_id AND p.org_id IS NULL;

-- 4. Accounting accounts (system accounts created with null org)
UPDATE accounting_accounts aa
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE aa.client_id = sub.client_id AND aa.org_id IS NULL;

-- 5. Accounting account mappings
UPDATE accounting_account_mappings aam
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE aam.client_id = sub.client_id AND aam.org_id IS NULL;

-- 6. Accounting payment method mappings
UPDATE accounting_payment_method_mappings apmm
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE apmm.client_id = sub.client_id AND apmm.org_id IS NULL;

-- 7. Journal entries (if any were created with null org)
UPDATE journal_entries je
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE je.client_id = sub.client_id AND je.org_id IS NULL;

-- 8. Accounting posting jobs
UPDATE accounting_posting_jobs apj
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE apj.client_id = sub.client_id AND apj.org_id IS NULL;

-- 9. Expense categories
UPDATE expense_categories ec
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE ec.client_id = sub.client_id AND ec.org_id IS NULL;

-- 10. Users (staff without branch assignment)
UPDATE users u
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE u.client_id = sub.client_id AND u.org_id IS NULL;

-- 11. Document sequences
UPDATE document_sequences ds
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE ds.client_id = sub.client_id AND ds.org_id IS NULL;

-- 12. System configurations
UPDATE system_configurations sc
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE sc.client_id = sub.client_id AND sc.org_id IS NULL;
