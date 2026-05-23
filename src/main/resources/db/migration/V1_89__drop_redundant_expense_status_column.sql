-- ==============================================================================
-- V1_89: Drop redundant 'status' column from expenses & 'document_kind', 'expense_category_id' from invoices
-- ==============================================================================

ALTER TABLE expenses DROP COLUMN IF EXISTS status;
ALTER TABLE invoices DROP COLUMN IF EXISTS document_kind;
ALTER TABLE invoices DROP COLUMN IF EXISTS expense_category_id;
