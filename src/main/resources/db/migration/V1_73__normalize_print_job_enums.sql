-- V1_73: Normalize print_jobs enum columns to uppercase so Hibernate
-- @Enumerated(EnumType.STRING) mappings never fail on old/lowercase rows.

UPDATE print_jobs SET job_kind = UPPER(job_kind) WHERE job_kind <> UPPER(job_kind);
UPDATE print_jobs SET job_kind = 'BILL' WHERE job_kind NOT IN ('KOT', 'BILL');

UPDATE print_jobs SET status = UPPER(status) WHERE status <> UPPER(status);
UPDATE print_jobs SET status = 'PENDING' WHERE status NOT IN ('PENDING', 'CLAIMED', 'PRINTED', 'FAILED', 'RETRY');

UPDATE print_jobs SET attempts = 0 WHERE attempts IS NULL;
