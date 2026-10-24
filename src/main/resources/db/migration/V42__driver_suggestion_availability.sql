-- Only registered drivers' work hours are retained from demand uploads.
CREATE TABLE driver_schedule_windows (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    source_import_id BIGINT NOT NULL,
    source_date DATE NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_suggestion_schedule_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_suggestion_schedule_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    CONSTRAINT fk_suggestion_schedule_import FOREIGN KEY (source_import_id) REFERENCES schedule_imports(id),
    CONSTRAINT uk_suggestion_schedule UNIQUE (organization_id, driver_id, starts_at, ends_at),
    INDEX idx_suggestion_schedule_date (organization_id, source_date)
);
