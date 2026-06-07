ALTER TABLE print_configurations
    ADD COLUMN IF NOT EXISTS source_station_id UUID,
    ADD COLUMN IF NOT EXISTS source_local_revision BIGINT;

CREATE INDEX IF NOT EXISTS idx_print_configuration_station_revision
    ON print_configurations (source_station_id, source_local_revision)
    WHERE source_station_id IS NOT NULL;
