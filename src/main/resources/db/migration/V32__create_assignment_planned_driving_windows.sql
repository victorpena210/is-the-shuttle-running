CREATE TABLE assignment_planned_driving_windows (
    driver_assignment_id BIGINT NOT NULL,
    window_order INT NOT NULL,
    window_start_time TIME NOT NULL,
    window_end_time TIME NOT NULL,
    PRIMARY KEY (driver_assignment_id, window_order),
    CONSTRAINT fk_assignment_planned_windows_assignment
        FOREIGN KEY (driver_assignment_id)
        REFERENCES driver_assignments (id)
        ON DELETE CASCADE
);
