ALTER TABLE driver_assignments
    ADD COLUMN source_schedule_import_id BIGINT NULL
        AFTER originally_suggested_driver_id;

ALTER TABLE driver_assignments
    ADD CONSTRAINT fk_driver_assignments_source_schedule_import
        FOREIGN KEY (source_schedule_import_id)
        REFERENCES schedule_imports (id)
        ON DELETE SET NULL;

CREATE INDEX idx_driver_assignments_source_schedule_import
    ON driver_assignments (source_schedule_import_id);
