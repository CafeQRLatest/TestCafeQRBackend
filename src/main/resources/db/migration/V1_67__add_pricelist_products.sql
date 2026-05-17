-- V1.67 Add Pricelist Products for specific product pricing per pricelist
DO $$ 
BEGIN 
    CREATE TABLE IF NOT EXISTS pricelist_products (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        client_id UUID REFERENCES clients(id),
        org_id UUID REFERENCES organizations(id),
        pricelist_id UUID REFERENCES pricelists(id) ON DELETE CASCADE,
        product_id UUID REFERENCES products(id) ON DELETE CASCADE,
        price DECIMAL(15, 2) NOT NULL,
        isactive CHAR(1) DEFAULT 'Y',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        created_by UUID,
        updated_by UUID
    );

    CREATE INDEX IF NOT EXISTS idx_pricelist_products_product ON pricelist_products(product_id);
    CREATE INDEX IF NOT EXISTS idx_pricelist_products_pricelist ON pricelist_products(pricelist_id);
    
    RAISE NOTICE 'V1.67 Pricelist products table created successfully.';
END $$;
