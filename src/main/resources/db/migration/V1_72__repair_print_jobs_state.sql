-- This file replaces the stale V1_72__repair_print_jobs_state.sql that was
-- left in target/classes from a previous build session. If Flyway already
-- applied V1_72, this is a no-op. The comprehensive normalization is in V1_73.

UPDATE print_jobs SET job_kind = UPPER(job_kind) WHERE job_kind <> UPPER(job_kind);
UPDATE print_jobs SET status = UPPER(status) WHERE status <> UPPER(status);
