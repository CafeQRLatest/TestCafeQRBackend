ALTER TABLE terminals
ADD COLUMN IF NOT EXISTS offline_billing_mode VARCHAR(30) DEFAULT 'NONE',
ADD COLUMN IF NOT EXISTS offline_billing_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS print_station_enabled BOOLEAN DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_terminals_main_offline_per_org
    ON terminals (client_id, org_id)
    WHERE offline_billing_mode = 'MAIN' AND isactive = 'Y';

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS source_device_id UUID,
ADD COLUMN IF NOT EXISTS source_terminal_id UUID,
ADD COLUMN IF NOT EXISTS source_operation_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_offline_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_local_ref VARCHAR(160),
ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS sync_origin VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_client_source_operation
    ON orders (client_id, source_operation_id)
    WHERE source_operation_id IS NOT NULL;

ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS source_device_id UUID,
ADD COLUMN IF NOT EXISTS source_terminal_id UUID,
ADD COLUMN IF NOT EXISTS source_operation_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_offline_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_local_ref VARCHAR(160),
ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS sync_origin VARCHAR(40);

ALTER TABLE payments
ADD COLUMN IF NOT EXISTS source_device_id UUID,
ADD COLUMN IF NOT EXISTS source_terminal_id UUID,
ADD COLUMN IF NOT EXISTS source_operation_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_offline_id VARCHAR(160),
ADD COLUMN IF NOT EXISTS source_local_ref VARCHAR(160),
ADD COLUMN IF NOT EXISTS offline_created_at TIMESTAMP WITHOUT TIME ZONE,
ADD COLUMN IF NOT EXISTS sync_origin VARCHAR(40);

CREATE TABLE IF NOT EXISTS offline_sequence_leases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID NOT NULL,
    terminal_id UUID NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    start_number BIGINT NOT NULL,
    end_number BIGINT NOT NULL,
    next_number BIGINT NOT NULL,
    prefix VARCHAR(60),
    suffix VARCHAR(60),
    padding_length INTEGER NOT NULL DEFAULT 7,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    lease_key VARCHAR(180) NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_offline_sequence_lease_key UNIQUE (client_id, lease_key)
);

CREATE INDEX IF NOT EXISTS idx_offline_sequence_leases_terminal
    ON offline_sequence_leases (client_id, org_id, terminal_id, document_type, status);

CREATE TABLE IF NOT EXISTS print_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID,
    order_id UUID,
    offline_operation_id VARCHAR(160),
    source_operation_id VARCHAR(160),
    source_terminal_id UUID,
    source_device_id UUID,
    target_terminal_id UUID,
    claimed_by_terminal_id UUID,
    job_kind VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    dedupe_key VARCHAR(220) NOT NULL,
    payload_json TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    claimed_at TIMESTAMP WITHOUT TIME ZONE,
    printed_at TIMESTAMP WITHOUT TIME ZONE,
    next_attempt_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_print_jobs_dedupe UNIQUE (client_id, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_print_jobs_claimable
    ON print_jobs (client_id, org_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_print_jobs_order
    ON print_jobs (client_id, order_id);
