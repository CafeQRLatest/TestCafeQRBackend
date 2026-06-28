-- V1_121: Create Client Subscription Modules table for multi-branch module (sachet) pricing
CREATE TABLE IF NOT EXISTS client_subscription_modules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    org_id UUID REFERENCES organizations(id) ON DELETE CASCADE, -- Nullable for account-level modules
    module_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, TRIAL, CANCEL_PENDING
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Ensure a client can have only one active record for an account-level module (where org_id is null)
CREATE UNIQUE INDEX IF NOT EXISTS uq_client_sub_module_account 
ON client_subscription_modules(client_id, module_name) 
WHERE org_id IS NULL;

-- Ensure a branch can have only one active record for a branch-level module (where org_id is not null)
CREATE UNIQUE INDEX IF NOT EXISTS uq_client_sub_module_branch 
ON client_subscription_modules(client_id, org_id, module_name) 
WHERE org_id IS NOT NULL;
