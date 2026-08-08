-- Migration V1_133 to V1_134: Add high-performance composite B-tree indexes for Purchase Order queries

-- 1. Composite Index for multi-tenant Purchase Order Date Range History queries
CREATE INDEX IF NOT EXISTS idx_orders_purchase_history 
ON orders (client_id, order_type, order_date DESC);

-- 2. Index for filtering Purchase Orders by Vendor
CREATE INDEX IF NOT EXISTS idx_orders_vendor 
ON orders (client_id, vendor_id) 
WHERE order_type = 'PURCHASE';

-- 3. Index for filtering Purchase Orders by Destination Warehouse
CREATE INDEX IF NOT EXISTS idx_orders_warehouse 
ON orders (client_id, warehouse_id) 
WHERE order_type = 'PURCHASE';

-- 4. Index for filtering Purchase Orders by Status
CREATE INDEX IF NOT EXISTS idx_orders_status 
ON orders (client_id, order_type, order_status);

-- 5. Index for fast lookup by Order Number
CREATE INDEX IF NOT EXISTS idx_orders_order_no 
ON orders (client_id, order_no);
