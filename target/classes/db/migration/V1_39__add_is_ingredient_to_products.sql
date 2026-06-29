-- V1_39__add_is_ingredient_to_products.sql
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_ingredient BOOLEAN DEFAULT FALSE;
