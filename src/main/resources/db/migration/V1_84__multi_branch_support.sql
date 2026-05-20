-- ═══════════════════════════════════════════════════════════════════════
-- V1_84: Multi-branch support indexes & constraints
-- ═══════════════════════════════════════════════════════════════════════

-- 1. Enable "all branches" queries for adjustments (Super Admin view)
CREATE INDEX IF NOT EXISTS idx_stock_adjustments_client
  ON stock_adjustments(client_id, adjustment_date DESC);

-- 2. Enable "all branches" queries for transfers (Super Admin view)
CREATE INDEX IF NOT EXISTS idx_stock_transfers_client
  ON stock_transfers(client_id, transfer_date DESC);

-- 3. Branch-scoped config: index for fast lookups
CREATE INDEX IF NOT EXISTS idx_sysconfig_client_org
  ON system_configurations(client_id, org_id);
