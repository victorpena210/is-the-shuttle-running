CREATE TABLE passenger_ride_confirmations (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,

    driver_name_snapshot VARCHAR(200) NOT NULL,

    device_token_hash CHAR(64) NOT NULL,

    ride_window_start DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_passenger_ride_assignment_device_window
        UNIQUE (
            assignment_id,
            device_token_hash,
            ride_window_start
        ),

    CONSTRAINT fk_passenger_ride_confirmation_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id),

    CONSTRAINT fk_passenger_ride_confirmation_assignment
        FOREIGN KEY (assignment_id)
        REFERENCES driver_assignments (id),

    CONSTRAINT fk_passenger_ride_confirmation_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers (id)
);

CREATE INDEX idx_passenger_ride_confirmation_org_submitted
    ON passenger_ride_confirmations (
        organization_id,
        submitted_at
    );

CREATE INDEX idx_passenger_ride_confirmation_assignment_submitted
    ON passenger_ride_confirmations (
        assignment_id,
        submitted_at
    );