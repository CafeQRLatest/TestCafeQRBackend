-- ==============================================================================
-- V1_90: Extend invoice_lines with missing descriptive columns (product, category, tax, discounts, etc.) to match order_lines
-- ==============================================================================

ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS product_id UUID;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS variant_id UUID;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS product_name VARCHAR(255);
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS category_name VARCHAR(150);
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS is_packaged_good BOOLEAN DEFAULT FALSE;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(20) DEFAULT 'units';
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5, 2) DEFAULT 0;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(15, 2) DEFAULT 0;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(15, 2) DEFAULT 0;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS isactive CHAR(1) DEFAULT 'Y';
