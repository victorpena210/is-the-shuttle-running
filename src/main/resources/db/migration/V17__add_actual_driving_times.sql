ALTER TABLE driver_assignments
    ADD COLUMN actual_started_at DATETIME(6) NULL
        AFTER scheduled_end_time,
    ADD COLUMN actual_ended_at DATETIME(6) NULL
        AFTER actual_started_at;
