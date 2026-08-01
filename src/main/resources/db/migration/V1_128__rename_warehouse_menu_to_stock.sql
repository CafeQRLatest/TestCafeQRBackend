-- V1_128__rename_warehouse_menu_to_stock.sql
-- Renames 'Warehouse Management' / 'Warehouses' menu entry to 'Stock & Inventory' pointing to '/owner/stock-menu'
DO $$
DECLARE
    wh_menu_id UUID;
BEGIN
    SELECT id INTO wh_menu_id FROM menus WHERE name IN ('Warehouses', 'Warehouse Management') LIMIT 1;
    
    IF wh_menu_id IS NOT NULL THEN
        UPDATE menus 
        SET name = 'Stock & Inventory',
            url = '/owner/stock-menu',
            description = 'Manage stock, transfers & valuations'
        WHERE id = wh_menu_id;
    END IF;
END $$;
