ALTER TABLE driver_assignments
    ADD COLUMN reviewed_schedule_import_id BIGINT NULL
        AFTER source_schedule_import_id,
    ADD COLUMN schedule_reviewed_at DATETIME(6) NULL
        AFTER reviewed_schedule_import_id,
    ADD COLUMN schedule_reviewed_by_user_id BIGINT NULL
        AFTER schedule_reviewed_at;

ALTER TABLE driver_assignments
    ADD CONSTRAINT fk_driver_assignments_reviewed_schedule_import
        FOREIGN KEY (reviewed_schedule_import_id)
        REFERENCES schedule_imports (id)
        ON DELETE SET NULL,
    ADD CONSTRAINT fk_driver_assignments_schedule_reviewed_by_user
        FOREIGN KEY (schedule_reviewed_by_user_id)
        REFERENCES users (id);

CREATE INDEX idx_driver_assignments_reviewed_schedule_import
    ON driver_assignments (reviewed_schedule_import_id);

CREATE INDEX idx_driver_assignments_schedule_reviewed_by_user
    ON driver_assignments (schedule_reviewed_by_user_id);
