-- V1_74: Document numbers are generated per client/org, so uniqueness must be scoped the same way.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_order_no_key;
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_no_key;

DROP INDEX IF EXISTS orders_order_no_key;
DROP INDEX IF EXISTS invoices_invoice_no_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_client_org_order_no
    ON orders (client_id, org_id, order_no)
    WHERE client_id IS NOT NULL AND org_id IS NOT NULL AND order_no IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_client_org_invoice_no
    ON invoices (client_id, org_id, invoice_no)
    WHERE client_id IS NOT NULL AND org_id IS NOT NULL AND invoice_no IS NOT NULL;
