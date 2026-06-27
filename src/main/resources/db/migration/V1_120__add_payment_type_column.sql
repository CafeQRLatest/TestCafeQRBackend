-- V1_120: Add payment_type column to payment_types table
ALTER TABLE payment_types ADD COLUMN IF NOT EXISTS payment_type VARCHAR(20) NOT NULL DEFAULT 'OTHERS';
