ALTER TABLE driver_assignments
    ADD COLUMN scheduled_start_time TIME NULL AFTER driver_id,
    ADD COLUMN scheduled_end_time TIME NULL AFTER scheduled_start_time,
    ADD COLUMN assignment_source VARCHAR(20) NOT NULL DEFAULT 'AUTO'
        AFTER scheduled_end_time;