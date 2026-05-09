-- V1_75: Add customer name/phone/ids to orders for POS customer linking
-- Also add customer_age_enabled config toggle

-- 1. Denormalized customer info on orders (for receipts / offline / fast reads)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_name VARCHAR(200);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_ids TEXT; -- JSON array of linked customer objects

-- 2. Configuration toggle for customer age field
ALTER TABLE system_configurations ADD COLUMN IF NOT EXISTS customer_age_enabled BOOLEAN DEFAULT FALSE;
