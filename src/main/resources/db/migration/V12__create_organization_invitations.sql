CREATE TABLE organization_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    invited_by_user_id BIGINT NOT NULL,

    invited_email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL,

    token_hash CHAR(64) NOT NULL,

    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_invitations_org_email
        UNIQUE (
            organization_id,
            invited_email
        ),

    CONSTRAINT uk_organization_invitations_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_organization_invitations_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_organization_invitations_invited_by
        FOREIGN KEY (invited_by_user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_organization_invitations_expires_at
    ON organization_invitations (expires_at);