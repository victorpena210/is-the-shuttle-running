CREATE TABLE driver_availability_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    work_windows VARCHAR(100) NOT NULL,
    imported_windows VARCHAR(1000) NOT NULL,
    previous_windows VARCHAR(1000) NOT NULL,
    use_imported BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(30) NOT NULL,
    source_digest VARCHAR(200) NULL,
    confirmed_at DATETIME(6) NOT NULL,
    confirmed_by VARCHAR(254) NOT NULL,
    CONSTRAINT fk_availability_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_availability_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    INDEX idx_availability_latest (organization_id, shift_date, driver_id, id),
    INDEX idx_availability_history (organization_id, driver_id, id)
);
ALTER TABLE driver_assignments ADD COLUMN availability_update_hold BOOLEAN NOT NULL DEFAULT FALSE;
