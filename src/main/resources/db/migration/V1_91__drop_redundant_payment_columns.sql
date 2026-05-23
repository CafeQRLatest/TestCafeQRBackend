-- ==============================================================================
-- V1_91: Drop redundant 'document_kind', 'expense_category_id', and 'status' columns from payments
-- ==============================================================================

ALTER TABLE payments DROP COLUMN IF EXISTS document_kind;
ALTER TABLE payments DROP COLUMN IF EXISTS expense_category_id;
ALTER TABLE payments DROP COLUMN IF EXISTS status;
