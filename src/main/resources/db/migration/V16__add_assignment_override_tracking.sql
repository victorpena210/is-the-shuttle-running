ALTER TABLE driver_assignments
    ADD COLUMN originally_suggested_driver_id BIGINT NULL
        AFTER driver_id,
    ADD COLUMN changed_at DATETIME(6) NULL
        AFTER assignment_source,
    ADD COLUMN change_reason VARCHAR(500) NULL
        AFTER changed_at;

UPDATE driver_assignments
SET originally_suggested_driver_id = driver_id
WHERE assignment_source = 'AUTO'
  AND originally_suggested_driver_id IS NULL;

ALTER TABLE driver_assignments
    ADD CONSTRAINT fk_driver_assignments_originally_suggested_driver
        FOREIGN KEY (originally_suggested_driver_id)
        REFERENCES drivers (id);

CREATE INDEX idx_driver_assignments_originally_suggested_driver
    ON driver_assignments (originally_suggested_driver_id);