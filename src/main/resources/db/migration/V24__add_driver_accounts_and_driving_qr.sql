ALTER TABLE drivers
    ADD COLUMN email VARCHAR(254) NULL AFTER name,
    ADD COLUMN user_id BIGINT NULL AFTER email;

CREATE UNIQUE INDEX uk_drivers_organization_email
    ON drivers (organization_id, email);

CREATE UNIQUE INDEX uk_drivers_user
    ON drivers (user_id);

ALTER TABLE drivers
    ADD CONSTRAINT fk_drivers_user
        FOREIGN KEY (user_id)
        REFERENCES users (id);


ALTER TABLE organization_invitations
    ADD COLUMN driver_id BIGINT NULL AFTER invited_by_user_id;

CREATE INDEX idx_organization_invitations_driver
    ON organization_invitations (driver_id);

ALTER TABLE organization_invitations
    ADD CONSTRAINT fk_organization_invitations_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers (id)
        ON DELETE CASCADE;


ALTER TABLE organizations
    ADD COLUMN driving_qr_code VARCHAR(64) NULL AFTER trial_ends_at;

UPDATE organizations
SET driving_qr_code = LOWER(REPLACE(UUID(), '-', ''))
WHERE driving_qr_code IS NULL;

ALTER TABLE organizations
    MODIFY driving_qr_code VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX uk_organizations_driving_qr_code
    ON organizations (driving_qr_code);