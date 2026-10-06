CREATE TABLE wardrobe_hours_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    rule_scope VARCHAR(10) NOT NULL,
    label VARCHAR(80) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    closing_time TIME NULL,
    changed_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_wardrobe_hours_organization FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_wardrobe_hours_org_dates (organization_id, starts_on, ends_on, created_at)
);
