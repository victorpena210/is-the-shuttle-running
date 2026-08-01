CREATE TABLE driver_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_date DATE NOT NULL,
    shuttle_period VARCHAR(20) NOT NULL,
    driver_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_driver_assignments_date_period
        UNIQUE (assignment_date, shuttle_period),

    CONSTRAINT uk_driver_assignments_date_driver
        UNIQUE (assignment_date, driver_id),

    CONSTRAINT fk_driver_assignments_driver
        FOREIGN KEY (driver_id) REFERENCES drivers (id)
);

CREATE INDEX idx_driver_assignments_driver_date
    ON driver_assignments (driver_id, assignment_date);