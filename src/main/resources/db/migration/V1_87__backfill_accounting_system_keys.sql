-- V1_87: Recover legacy default accounting accounts that were created before
-- system_key metadata was consistently assigned.

WITH templates(system_key, code, account_type, account_sub_type, cash_account, bank_account) AS (
    VALUES
        ('CASH', 'SYS-1000', 'ASSET', 'Cash', TRUE, FALSE),
        ('BANK_UPI_CLEARING', 'SYS-1010', 'ASSET', 'Bank', FALSE, TRUE),
        ('ACCOUNTS_RECEIVABLE', 'SYS-1100', 'ASSET', 'Receivable', FALSE, FALSE),
        ('INVENTORY_ASSET', 'SYS-1200', 'ASSET', 'Inventory', FALSE, FALSE),
        ('INPUT_TAX', 'SYS-1300', 'ASSET', 'Tax', FALSE, FALSE),
        ('ACCOUNTS_PAYABLE', 'SYS-2000', 'LIABILITY', 'Payable', FALSE, FALSE),
        ('OUTPUT_TAX', 'SYS-2100', 'LIABILITY', 'Tax', FALSE, FALSE),
        ('SALES_REVENUE', 'SYS-4000', 'INCOME', 'Sales', FALSE, FALSE),
        ('PURCHASE_COGS', 'SYS-5000', 'EXPENSE', 'COGS', FALSE, FALSE),
        ('OPERATING_EXPENSES', 'SYS-5100', 'EXPENSE', 'Expense', FALSE, FALSE),
        ('DISCOUNT_ALLOWED', 'SYS-5200', 'EXPENSE', 'Discount', FALSE, FALSE),
        ('ROUND_OFF', 'SYS-5300', 'EXPENSE', 'Round Off', FALSE, FALSE),
        ('STOCK_ADJUSTMENT_GAIN_LOSS', 'SYS-5400', 'EXPENSE', 'Stock Adjustment', FALSE, FALSE)
)
UPDATE accounting_accounts a
SET system_key = t.system_key,
    is_system_account = TRUE,
    account_type = t.account_type,
    account_sub_type = t.account_sub_type,
    is_cash_account = t.cash_account,
    is_bank_account = t.bank_account,
    updated_at = CURRENT_TIMESTAMP
FROM templates t
WHERE UPPER(a.code) = t.code
  AND (a.system_key IS NULL OR a.system_key = '' OR UPPER(a.system_key) = t.system_key);

WITH templates(system_key, code) AS (
    VALUES
        ('CASH', 'SYS-1000'),
        ('BANK_UPI_CLEARING', 'SYS-1010'),
        ('ACCOUNTS_RECEIVABLE', 'SYS-1100'),
        ('INVENTORY_ASSET', 'SYS-1200'),
        ('INPUT_TAX', 'SYS-1300'),
        ('ACCOUNTS_PAYABLE', 'SYS-2000'),
        ('OUTPUT_TAX', 'SYS-2100'),
        ('SALES_REVENUE', 'SYS-4000'),
        ('PURCHASE_COGS', 'SYS-5000'),
        ('OPERATING_EXPENSES', 'SYS-5100'),
        ('DISCOUNT_ALLOWED', 'SYS-5200'),
        ('ROUND_OFF', 'SYS-5300'),
        ('STOCK_ADJUSTMENT_GAIN_LOSS', 'SYS-5400')
)
INSERT INTO accounting_account_mappings (
    id, client_id, org_id, mapping_key, account_id, description, isactive, created_at, updated_at
)
SELECT gen_random_uuid(), a.client_id, a.org_id, t.system_key, a.id,
       'System default mapping', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM accounting_accounts a
JOIN templates t ON UPPER(a.system_key) = t.system_key
ON CONFLICT (client_id, org_id, mapping_key)
DO UPDATE SET account_id = EXCLUDED.account_id,
              description = EXCLUDED.description,
              isactive = 'Y',
              updated_at = CURRENT_TIMESTAMP;

WITH payment_defaults(payment_method, account_key) AS (
    VALUES
        ('CASH', 'CASH'),
        ('ONLINE', 'BANK_UPI_CLEARING'),
        ('UPI', 'BANK_UPI_CLEARING'),
        ('CARD', 'BANK_UPI_CLEARING'),
        ('BANK', 'BANK_UPI_CLEARING'),
        ('CHEQUE', 'BANK_UPI_CLEARING'),
        ('MIXED', 'BANK_UPI_CLEARING')
)
INSERT INTO accounting_payment_method_mappings (
    id, client_id, org_id, payment_method, account_id, description, isactive, created_at, updated_at
)
SELECT gen_random_uuid(), a.client_id, a.org_id, p.payment_method, a.id,
       'Payment method account mapping', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM payment_defaults p
JOIN accounting_accounts a ON UPPER(a.system_key) = p.account_key
ON CONFLICT (client_id, org_id, payment_method)
DO UPDATE SET account_id = EXCLUDED.account_id,
              description = EXCLUDED.description,
              isactive = 'Y',
              updated_at = CURRENT_TIMESTAMP;
