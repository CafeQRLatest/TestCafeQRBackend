-- V1.68 Add default_pricelist_id to products
DO $$ 
BEGIN 
    ALTER TABLE products ADD COLUMN IF NOT EXISTS default_pricelist_id UUID REFERENCES pricelists(id);
    
    RAISE NOTICE 'V1.68 Added default_pricelist_id to products table.';
END $$;
