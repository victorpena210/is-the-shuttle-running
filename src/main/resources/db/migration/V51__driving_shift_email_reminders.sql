ALTER TABLE drivers ADD COLUMN driving_reminder_email VARCHAR(254) NULL;

CREATE TABLE driving_shift_email_reminders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    work_starts_at DATETIME(6) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    last_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    CONSTRAINT uk_driving_shift_email UNIQUE (organization_id, driver_id, work_starts_at),
    CONSTRAINT fk_driving_shift_email_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_driving_shift_email_driver FOREIGN KEY (driver_id) REFERENCES drivers(id)
);
