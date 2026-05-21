-- Organization-level expenses use org_id = null. Document sequences must support
-- that same scope, while existing branch-scoped rows remain unchanged.
ALTER TABLE document_sequences ALTER COLUMN org_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_sequences_client_global_type
    ON document_sequences (client_id, document_type)
    WHERE org_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_expenses_client_global_expense_no
    ON expenses (client_id, expense_no)
    WHERE client_id IS NOT NULL AND org_id IS NULL AND expense_no IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_client_global_invoice_no
    ON invoices (client_id, invoice_no)
    WHERE client_id IS NOT NULL AND org_id IS NULL AND invoice_no IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_client_global_reference_no
    ON payments (client_id, reference_no)
    WHERE client_id IS NOT NULL AND org_id IS NULL AND reference_no IS NOT NULL;
