-- ═══════════════════════════════════════════════════════════════════════════════
-- V1_85: Backfill org_id for legacy records created before multi-branch support
-- ═══════════════════════════════════════════════════════════════════════════════

-- 1. Create a temp table of client default organizations for fast reuse
CREATE TEMP TABLE client_default_orgs AS
SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
FROM organizations WHERE isactive = 'Y'
ORDER BY client_id, created_at ASC;

-- 2. Deduplicate orders by appending a suffix if order_no conflicts
UPDATE orders o_null
SET order_no = o_null.order_no || '-LEGACY'
FROM client_default_orgs cdo
WHERE o_null.client_id = cdo.client_id
  AND o_null.org_id IS NULL
  AND EXISTS (
      SELECT 1 FROM orders o_target
      WHERE o_target.client_id = o_null.client_id
        AND o_target.org_id = cdo.default_org_id
        AND o_target.order_no = o_null.order_no
  );

UPDATE orders o
SET org_id = sub.default_org_id
FROM client_default_orgs sub
WHERE o.client_id = sub.client_id AND o.org_id IS NULL;

-- 3. Deduplicate invoices by appending a suffix if invoice_no conflicts
UPDATE invoices i_null
SET invoice_no = i_null.invoice_no || '-LEGACY'
FROM client_default_orgs cdo
WHERE i_null.client_id = cdo.client_id
  AND i_null.org_id IS NULL
  AND EXISTS (
      SELECT 1 FROM invoices i_target
      WHERE i_target.client_id = i_null.client_id
        AND i_target.org_id = cdo.default_org_id
        AND i_target.invoice_no = i_null.invoice_no
  );

UPDATE invoices i
SET org_id = sub.default_org_id
FROM client_default_orgs sub
WHERE i.client_id = sub.client_id AND i.org_id IS NULL;

-- 4. Payments
UPDATE payments p
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE p.client_id = sub.client_id AND p.org_id IS NULL;

-- 5. Deduplicate and merge accounting accounts
CREATE TEMP TABLE duplicate_accounts AS
SELECT 
    aa_null.id AS null_id,
    aa_target.id AS target_id
FROM accounting_accounts aa_null
JOIN client_default_orgs cdo ON aa_null.client_id = cdo.client_id
JOIN accounting_accounts aa_target ON aa_target.client_id = aa_null.client_id
    AND aa_target.org_id = cdo.default_org_id
    AND (
        aa_target.code = aa_null.code 
        OR (aa_target.system_key IS NOT NULL AND aa_target.system_key = aa_null.system_key)
    )
WHERE aa_null.org_id IS NULL;

-- Redirect references to duplicate accounting accounts
UPDATE journal_lines jl
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE jl.account_id = da.null_id;

UPDATE party_ledger_entries ple
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE ple.account_id = da.null_id;

UPDATE accounting_accounts aa
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE aa.account_id = da.null_id;

UPDATE accounting_account_mappings aam
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE aam.account_id = da.null_id;

UPDATE accounting_payment_method_mappings apmm
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE apmm.account_id = da.null_id;

UPDATE expense_categories ec
SET account_id = da.target_id
FROM duplicate_accounts da
WHERE ec.account_id = da.null_id;

-- Delete duplicate null-org accounting accounts
DELETE FROM accounting_accounts
WHERE id IN (SELECT null_id FROM duplicate_accounts);

-- Update remaining non-duplicate null-org accounting accounts
UPDATE accounting_accounts aa
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE aa.client_id = cdo.client_id AND aa.org_id IS NULL;

-- 6. Accounting account mappings (merge/deduplicate)
DELETE FROM accounting_account_mappings aam_null
USING client_default_orgs cdo, accounting_account_mappings aam_target
WHERE aam_null.client_id = cdo.client_id
  AND aam_null.org_id IS NULL
  AND aam_target.client_id = aam_null.client_id
  AND aam_target.org_id = cdo.default_org_id
  AND UPPER(aam_target.mapping_key) = UPPER(aam_null.mapping_key);

UPDATE accounting_account_mappings aam
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE aam.client_id = cdo.client_id AND aam.org_id IS NULL;

-- 7. Accounting payment method mappings (merge/deduplicate)
DELETE FROM accounting_payment_method_mappings apmm_null
USING client_default_orgs cdo, accounting_payment_method_mappings apmm_target
WHERE apmm_null.client_id = cdo.client_id
  AND apmm_null.org_id IS NULL
  AND apmm_target.client_id = apmm_null.client_id
  AND apmm_target.org_id = cdo.default_org_id
  AND UPPER(apmm_target.payment_method) = UPPER(apmm_null.payment_method);

UPDATE accounting_payment_method_mappings apmm
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE apmm.client_id = cdo.client_id AND apmm.org_id IS NULL;

-- 8. Deduplicate and merge journal entries
-- First, delete duplicate null-org party ledger entries for duplicate journal entries
DELETE FROM party_ledger_entries ple
WHERE ple.journal_entry_id IN (
    SELECT je_null.id
    FROM journal_entries je_null
    JOIN client_default_orgs cdo ON je_null.client_id = cdo.client_id
    JOIN journal_entries je_target ON je_target.client_id = je_null.client_id
        AND je_target.org_id = cdo.default_org_id
        AND je_target.source_type = je_null.source_type
        AND je_target.source_id = je_null.source_id
        AND je_target.status <> 'VOID'
    WHERE je_null.org_id IS NULL
);

-- Delete duplicate null-org journal entries (cascades to journal_lines)
DELETE FROM journal_entries je_null
USING client_default_orgs cdo, journal_entries je_target
WHERE je_null.client_id = cdo.client_id
  AND je_null.org_id IS NULL
  AND je_target.client_id = je_null.client_id
  AND je_target.org_id = cdo.default_org_id
  AND je_target.source_type = je_null.source_type
  AND je_target.source_id = je_null.source_id
  AND je_target.status <> 'VOID';

-- Update remaining ones
UPDATE journal_entries je
SET org_id = sub.default_org_id
FROM client_default_orgs sub
WHERE je.client_id = sub.client_id AND je.org_id IS NULL;

-- 9. Accounting posting jobs (deduplicate)
DELETE FROM accounting_posting_jobs apj_null
USING client_default_orgs cdo, accounting_posting_jobs apj_target
WHERE apj_null.client_id = cdo.client_id
  AND apj_null.org_id IS NULL
  AND apj_target.client_id = apj_null.client_id
  AND apj_target.org_id = cdo.default_org_id
  AND apj_target.source_type = apj_null.source_type
  AND apj_target.source_id = apj_null.source_id;

UPDATE accounting_posting_jobs apj
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE apj.client_id = cdo.client_id AND apj.org_id IS NULL;

-- 10. Expense categories (deduplicate)
CREATE TEMP TABLE duplicate_expense_categories AS
SELECT 
    ec_null.id AS null_id,
    ec_target.id AS target_id
FROM expense_categories ec_null
JOIN client_default_orgs cdo ON ec_null.client_id = cdo.client_id
JOIN expense_categories ec_target ON ec_target.client_id = ec_null.client_id
    AND ec_target.org_id = cdo.default_org_id
    AND LOWER(ec_target.name) = LOWER(ec_null.name)
    AND (
        (ec_target.created_by IS NULL AND ec_null.created_by IS NULL)
        OR (ec_target.created_by = ec_null.created_by)
    )
WHERE ec_null.org_id IS NULL;

UPDATE orders SET expense_category_id = dec.target_id
FROM duplicate_expense_categories dec
WHERE expense_category_id = dec.null_id;

UPDATE invoices SET expense_category_id = dec.target_id
FROM duplicate_expense_categories dec
WHERE expense_category_id = dec.null_id;

UPDATE payments SET expense_category_id = dec.target_id
FROM duplicate_expense_categories dec
WHERE expense_category_id = dec.null_id;

UPDATE expenses SET category_id = dec.target_id
FROM duplicate_expense_categories dec
WHERE category_id = dec.null_id;

DELETE FROM expense_categories
WHERE id IN (SELECT null_id FROM duplicate_expense_categories);

UPDATE expense_categories ec
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE ec.client_id = cdo.client_id AND ec.org_id IS NULL;

-- 11. Expenses (backfill org_id)
-- First resolve idempotency key conflicts if any
UPDATE expenses e_null
SET idempotency_key = e_null.idempotency_key || '-LEGACY'
FROM client_default_orgs cdo
WHERE e_null.client_id = cdo.client_id
  AND e_null.org_id IS NULL
  AND e_null.idempotency_key IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM expenses e_target
      WHERE e_target.client_id = e_null.client_id
        AND e_target.org_id = cdo.default_org_id
        AND e_target.idempotency_key = e_null.idempotency_key
  );

UPDATE expenses e
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE e.client_id = cdo.client_id AND e.org_id IS NULL;

-- 12. Users (staff without branch assignment)
UPDATE users u
SET org_id = sub.default_org_id
FROM (
    SELECT DISTINCT ON (client_id) client_id, id AS default_org_id
    FROM organizations WHERE isactive = 'Y'
    ORDER BY client_id, created_at ASC
) sub
WHERE u.client_id = sub.client_id AND u.org_id IS NULL;

-- 13. Document sequences
DELETE FROM document_sequences ds_null
USING client_default_orgs cdo, document_sequences ds_target
WHERE ds_null.client_id = cdo.client_id
  AND ds_null.org_id IS NULL
  AND ds_target.client_id = ds_null.client_id
  AND ds_target.org_id = cdo.default_org_id
  AND ds_target.document_type = ds_null.document_type;

UPDATE document_sequences ds
SET org_id = cdo.default_org_id
FROM client_default_orgs cdo
WHERE ds.client_id = cdo.client_id AND ds.org_id IS NULL;

-- NOTE: System configurations with org_id IS NULL are intentional (representing
-- default tenant configurations). We do NOT update system_configurations.org_id here.

-- 14. Drop temp tables
DROP TABLE IF EXISTS client_default_orgs;
DROP TABLE IF EXISTS duplicate_accounts;
DROP TABLE IF EXISTS duplicate_expense_categories;
