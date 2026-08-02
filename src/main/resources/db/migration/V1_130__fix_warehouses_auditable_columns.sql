-- V1.130 Fix Auditable Columns Type for Warehouses, Recipes, and Inventory Tables
-- AuditableEntity in Java uses String (VARCHAR), but several migration scripts created created_by and updated_by as UUID.

DO $$
DECLARE
    t_name text;
BEGIN
    FOR t_name IN 
        SELECT unnest(ARRAY[
            'warehouses',
            'stock_transfers',
            'stock_adjustments',
            'stock_ledgers',
            'product_recipes',
            'pricelist_products',
            'waste_records',
            'waste_items'
        ])
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = t_name AND column_name = 'created_by') THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN created_by TYPE VARCHAR(255) USING created_by::varchar', t_name);
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = t_name AND column_name = 'updated_by') THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN updated_by TYPE VARCHAR(255) USING updated_by::varchar', t_name);
        END IF;
    END LOOP;
END $$;
