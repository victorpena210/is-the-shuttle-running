ALTER TABLE driver_assignments
    ADD COLUMN scheduled_start_time TIME NULL,
    ADD COLUMN scheduled_end_time TIME NULL;
    