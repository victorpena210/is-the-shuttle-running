-- Keep the original start-time snapshot for audit. A verified assignment
-- correction affects coverage attribution only, never actual driving time.
ALTER TABLE driving_coverage_windows
    ADD COLUMN corrected_driver_id BIGINT NULL,
    ADD COLUMN assignment_correction_reason VARCHAR(1000) NULL;
