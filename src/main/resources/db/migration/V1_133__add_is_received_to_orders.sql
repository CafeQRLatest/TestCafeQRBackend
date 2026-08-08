-- Flyway migration V1_133: Add non-mandatory is_received column to orders table
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_received BOOLEAN DEFAULT FALSE;

-- Backfill existing completed purchase orders so historical records remain accurate
UPDATE orders SET is_received = TRUE WHERE order_type = 'PURCHASE' AND order_status = 'COMPLETED';
