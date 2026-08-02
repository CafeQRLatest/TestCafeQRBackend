-- V1.130 Fix Auditable Columns Type for Warehouses and Inventory Tables
-- AuditableEntity in Java uses String (VARCHAR), but V1.27 created created_by and updated_by as UUID.

DO $$
DECLARE
    t_name text;
BEGIN
    FOR t_name IN 
        SELECT unnest(ARRAY[
            'warehouses',
            'stock_transfers',
            'stock_adjustments'
        ])
    LOOP
        EXECUTE format('ALTER TABLE %I ALTER COLUMN created_by TYPE VARCHAR(255) USING created_by::varchar', t_name);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN updated_by TYPE VARCHAR(255) USING updated_by::varchar', t_name);
    END LOOP;
END $$;
