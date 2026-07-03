-- V1.122 Migrate Subscription to Branches and create payment tracking table
ALTER TABLE organizations 
ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(50) DEFAULT 'TRIAL',
ADD COLUMN IF NOT EXISTS subscription_expiry_date TIMESTAMP DEFAULT (CURRENT_TIMESTAMP + INTERVAL '14 days');

-- Copy existing client subscription status & expiry to organization branches
UPDATE organizations o
SET subscription_status = COALESCE(c.subscription_status, 'TRIAL'),
    subscription_expiry_date = COALESCE(c.subscription_expiry_date, CURRENT_TIMESTAMP + INTERVAL '14 days')
FROM clients c
WHERE o.client_id = c.id;

-- Create subscription_payments table to prevent replay/double activation attacks
CREATE TABLE IF NOT EXISTS subscription_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID,
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_payment_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);
