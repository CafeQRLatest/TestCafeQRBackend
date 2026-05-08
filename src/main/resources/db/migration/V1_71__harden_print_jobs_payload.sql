ALTER TABLE print_jobs
    ALTER COLUMN payload_json TYPE TEXT USING payload_json::text;

UPDATE print_jobs
SET status = 'PENDING'
WHERE status IS NULL;

UPDATE print_jobs
SET job_kind = 'BILL'
WHERE job_kind IS NULL;

UPDATE print_jobs
SET attempts = 0
WHERE attempts IS NULL;
