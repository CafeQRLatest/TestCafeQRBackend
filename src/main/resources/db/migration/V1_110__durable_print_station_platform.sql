ALTER TABLE print_jobs
    ADD COLUMN IF NOT EXISTS leased_by_station_id UUID,
    ADD COLUMN IF NOT EXISTS lease_token VARCHAR(80),
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS local_queued_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS spool_job_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS printer_profile_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS route_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS output_format VARCHAR(30),
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS ambiguous BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_print_jobs_station_claim
    ON print_jobs (client_id, org_id, target_terminal_id, source_terminal_id, status, lease_expires_at, created_at);

CREATE TABLE IF NOT EXISTS print_stations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID NOT NULL,
    terminal_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    station_token_hash VARCHAR(64),
    pairing_code_hash VARCHAR(64),
    pairing_expires_at TIMESTAMP WITHOUT TIME ZONE,
    paired_at TIMESTAMP WITHOUT TIME ZONE,
    last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE,
    service_version VARCHAR(60),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    capabilities_json TEXT,
    fallback_for_branch BOOLEAN NOT NULL DEFAULT FALSE,
    isactive VARCHAR(1) NOT NULL DEFAULT 'Y',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uq_print_station_terminal UNIQUE (client_id, terminal_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_print_station_branch_fallback
    ON print_stations (client_id, org_id)
    WHERE fallback_for_branch = TRUE AND isactive = 'Y';

CREATE INDEX IF NOT EXISTS idx_print_station_token
    ON print_stations (station_token_hash)
    WHERE station_token_hash IS NOT NULL AND isactive = 'Y';

CREATE TABLE IF NOT EXISTS print_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL,
    org_id UUID,
    scope_type VARCHAR(30) NOT NULL,
    scope_id UUID,
    revision INTEGER NOT NULL DEFAULT 1,
    settings_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_print_configuration_scope
    ON print_configurations (client_id, scope_type, COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::uuid));

CREATE INDEX IF NOT EXISTS idx_print_configuration_effective
    ON print_configurations (client_id, org_id, scope_type, scope_id);

CREATE TABLE IF NOT EXISTS print_job_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    print_job_id UUID NOT NULL REFERENCES print_jobs(id) ON DELETE CASCADE,
    station_id UUID REFERENCES print_stations(id) ON DELETE SET NULL,
    attempt_number INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(40) NOT NULL,
    message TEXT,
    failure_code VARCHAR(80),
    spool_job_id VARCHAR(120),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_print_job_attempts_job
    ON print_job_attempts (print_job_id, created_at);
