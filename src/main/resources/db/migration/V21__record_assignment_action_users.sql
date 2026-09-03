ALTER TABLE driver_assignments
    ADD COLUMN changed_by_user_id BIGINT NULL
        AFTER changed_at,
    ADD COLUMN actual_started_by_user_id BIGINT NULL
        AFTER actual_started_at,
    ADD COLUMN actual_ended_by_user_id BIGINT NULL
        AFTER actual_ended_at;

ALTER TABLE driver_assignments
    ADD CONSTRAINT fk_driver_assignments_changed_by_user
        FOREIGN KEY (changed_by_user_id)
        REFERENCES users (id),
    ADD CONSTRAINT fk_driver_assignments_actual_started_by_user
        FOREIGN KEY (actual_started_by_user_id)
        REFERENCES users (id),
    ADD CONSTRAINT fk_driver_assignments_actual_ended_by_user
        FOREIGN KEY (actual_ended_by_user_id)
        REFERENCES users (id);

CREATE INDEX idx_driver_assignments_changed_by_user
    ON driver_assignments (changed_by_user_id);

CREATE INDEX idx_driver_assignments_actual_started_by_user
    ON driver_assignments (actual_started_by_user_id);

CREATE INDEX idx_driver_assignments_actual_ended_by_user
    ON driver_assignments (actual_ended_by_user_id);
