-- V1.121 Create Client Subscription Modules Table
CREATE TABLE IF NOT EXISTS client_subscription_modules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID,
    module_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_client_sub_module_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

-- Unique indexes to prevent duplicate module records per client/org
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_sub_mod_unique_org 
    ON client_subscription_modules (client_id, org_id, module_name) 
    WHERE org_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_client_sub_mod_unique_no_org 
    ON client_subscription_modules (client_id, module_name) 
    WHERE org_id IS NULL;

-- Performance index for client module lookups
CREATE INDEX IF NOT EXISTS idx_client_sub_mod_client_id 
    ON client_subscription_modules (client_id);
