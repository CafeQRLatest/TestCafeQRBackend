-- V1_122__add_uom_precision_to_lines.sql
-- Add uom_precision column to order_lines and invoice_lines

ALTER TABLE order_lines ADD COLUMN uom_precision INTEGER DEFAULT 0;
ALTER TABLE invoice_lines ADD COLUMN uom_precision INTEGER DEFAULT 0;
