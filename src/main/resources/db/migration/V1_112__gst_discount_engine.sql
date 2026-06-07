-- ============================================================
-- V1_110: GST-Compliant Discount & Settlement Engine Schema
-- ============================================================
-- Adds audit-grade GST enrichment columns across five tables.
-- Design constraints:
--   round_off_amount lives on PAYMENTS only (not orders/invoices).
--   invoice_lines are immutable snapshots — reuse for credit notes.
--   tax_snapshot_rate: never depend on master tax tables historically.
-- ============================================================

-- ──────────────────────────────────────────────────────────────
-- ORDER LINES
-- ──────────────────────────────────────────────────────────────
ALTER TABLE order_lines
  ADD COLUMN IF NOT EXISTS gross_line_amount        DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS unit_price_ex_tax        DECIMAL(15,4),
  ADD COLUMN IF NOT EXISTS taxable_amount           DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS tax_type                 VARCHAR(10),
  ADD COLUMN IF NOT EXISTS tax_snapshot_rate        DECIMAL(7,4),
  ADD COLUMN IF NOT EXISTS tax_code                 VARCHAR(30),
  ADD COLUMN IF NOT EXISTS tax_name                 VARCHAR(100),
  ADD COLUMN IF NOT EXISTS manual_discount_amount   DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS manual_discount_percent  DECIMAL(7,4),
  ADD COLUMN IF NOT EXISTS allocated_order_discount DECIMAL(15,2);

-- ──────────────────────────────────────────────────────────────
-- INVOICE LINES
-- Mirrors order_lines exactly — immutable snapshots for credit notes.
-- IMPORTANT: These values must NOT be re-derived from master tables.
-- ──────────────────────────────────────────────────────────────
ALTER TABLE invoice_lines
  ADD COLUMN IF NOT EXISTS gross_line_amount        DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS unit_price_ex_tax        DECIMAL(15,4),
  ADD COLUMN IF NOT EXISTS taxable_amount           DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS tax_type                 VARCHAR(10),
  ADD COLUMN IF NOT EXISTS tax_snapshot_rate        DECIMAL(7,4),
  ADD COLUMN IF NOT EXISTS tax_code                 VARCHAR(30),
  ADD COLUMN IF NOT EXISTS tax_name                 VARCHAR(100),
  ADD COLUMN IF NOT EXISTS manual_discount_amount   DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS manual_discount_percent  DECIMAL(7,4),
  ADD COLUMN IF NOT EXISTS allocated_order_discount DECIMAL(15,2);

-- ──────────────────────────────────────────────────────────────
-- ORDERS
-- NOTE: round_off_amount is intentionally NOT here.
--       The same order can be paid by UPI (no round-off) or
--       Cash (round-off). Round-off belongs on the payment.
--       total_discount_amount, total_tax_amount, total_amount,
--       grand_total already exist.
-- ──────────────────────────────────────────────────────────────
ALTER TABLE orders
  ADD COLUMN IF NOT EXISTS gross_amount                  DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS order_discount_type           VARCHAR(10),
  ADD COLUMN IF NOT EXISTS order_discount_value          DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS discount_source               VARCHAR(30),
  ADD COLUMN IF NOT EXISTS discount_calculation_version  VARCHAR(30);

-- ──────────────────────────────────────────────────────────────
-- INVOICES
-- NOTE: round_off_amount is intentionally NOT here.
--       Invoice is a GST document. Rounding must never alter it.
-- ──────────────────────────────────────────────────────────────
ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS gross_amount                  DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS total_tax_amount              DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS total_discount_amount         DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS taxable_amount                DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS discount_source               VARCHAR(30),
  ADD COLUMN IF NOT EXISTS discount_calculation_version  VARCHAR(30);

-- ──────────────────────────────────────────────────────────────
-- PAYMENTS
-- Round-off lives HERE and only here.
-- Invariant: amount_paid = invoice_total + round_off_amount
-- Example:   2157.00    = 2156.97      + 0.03
-- ──────────────────────────────────────────────────────────────
ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS invoice_total    DECIMAL(15,2),
  ADD COLUMN IF NOT EXISTS round_off_amount DECIMAL(15,2);
