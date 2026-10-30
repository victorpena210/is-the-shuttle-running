-- Calendar imports replace pickup_shifts; outcomes and deliveries use the rider and clock-out instead.
CREATE TABLE pickup_outcomes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    rider_id BIGINT NOT NULL,
    clock_out DATETIME(6) NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(254) NOT NULL,
    CONSTRAINT uk_pickup_outcome UNIQUE (organization_id, rider_id, clock_out),
    CONSTRAINT fk_pickup_outcome_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_pickup_outcome_rider FOREIGN KEY (rider_id) REFERENCES pickup_riders(id)
);
CREATE TABLE pickup_reminder_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    rider_id BIGINT NOT NULL,
    clock_out DATETIME(6) NOT NULL,
    driver_id BIGINT NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    last_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    CONSTRAINT uk_pickup_reminder UNIQUE (organization_id, rider_id, clock_out, driver_id),
    CONSTRAINT fk_pickup_reminder_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_pickup_reminder_rider FOREIGN KEY (rider_id) REFERENCES pickup_riders(id),
    CONSTRAINT fk_pickup_reminder_driver FOREIGN KEY (driver_id) REFERENCES drivers(id)
);
