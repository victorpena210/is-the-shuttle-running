CREATE TABLE park_calendar_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    operating_date DATE NOT NULL,
    calendar_status VARCHAR(16) NOT NULL,
    override_status VARCHAR(16) NOT NULL,
    calendar_source VARCHAR(80) NULL,
    diagnostic_code VARCHAR(120) NULL,
    reason VARCHAR(500) NOT NULL,
    confirmed_by_user_id BIGINT NULL,
    confirmed_by_name VARCHAR(220) NOT NULL,
    confirmed_by_email VARCHAR(254) NOT NULL,
    confirmed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_park_calendar_overrides_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_park_calendar_overrides_org_date
        (organization_id, operating_date, confirmed_at)
);
