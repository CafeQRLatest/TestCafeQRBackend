CREATE TABLE push_device_tokens (
    id UUID PRIMARY KEY,
    client_id UUID,
    org_id UUID,
    user_id UUID,
    device_token VARCHAR(512) UNIQUE NOT NULL,
    platform VARCHAR(50),
    enabled BOOLEAN DEFAULT TRUE,
    notify_kitchen BOOLEAN DEFAULT TRUE,
    notify_takeaway BOOLEAN DEFAULT TRUE,
    notify_delivery BOOLEAN DEFAULT TRUE,
    notify_settled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX idx_push_device_tokens_client_id ON push_device_tokens(client_id);
CREATE INDEX idx_push_device_tokens_device_token ON push_device_tokens(device_token);
