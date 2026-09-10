CREATE TABLE operational_status_updates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    status_override VARCHAR(40) NOT NULL,
    operational_message VARCHAR(500) NULL,
    updated_by_user_id BIGINT NULL,
    updated_by_name VARCHAR(220) NOT NULL,
    updated_by_email VARCHAR(254) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_operational_status_updates_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_operational_status_updates_org_updated
        (organization_id, updated_at)
);
