CREATE INDEX IF NOT EXISTS idx_journal_entries_scope_status_date
    ON journal_entries(client_id, org_id, status, isactive, entry_date DESC);

CREATE INDEX IF NOT EXISTS idx_journal_entries_scope_source_status
    ON journal_entries(client_id, org_id, source_type, source_id, status, isactive);

CREATE INDEX IF NOT EXISTS idx_journal_lines_entry_account
    ON journal_lines(journal_entry_id, account_id);

CREATE INDEX IF NOT EXISTS idx_orders_scope_status_date
    ON orders(client_id, org_id, order_type, order_status, doc_status, isactive, order_date);

CREATE INDEX IF NOT EXISTS idx_invoices_scope_date_status
    ON invoices(client_id, org_id, invoice_date, status, doc_status, isactive);

CREATE INDEX IF NOT EXISTS idx_invoices_order_id_runtime
    ON invoices(order_id);

CREATE INDEX IF NOT EXISTS idx_payments_scope_date_status
    ON payments(client_id, org_id, payment_date, payment_type, doc_status, isactive);

CREATE INDEX IF NOT EXISTS idx_payments_order_id_runtime
    ON payments(order_id);
