-- Migration to clean up the obsolete 'Point of Sale' menu item (url: '/counter')
-- The active POS screen menu item is 'Sales' (url: '/owner/sales')

-- Delete role mappings for the legacy Point of Sale menu
DELETE FROM role_menus 
WHERE menu_id IN (SELECT id FROM menus WHERE name = 'Point of Sale');

-- Delete the legacy Point of Sale menu item itself
DELETE FROM menus 
WHERE name = 'Point of Sale';
