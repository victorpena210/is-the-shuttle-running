CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,

    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,

    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_users_email
        UNIQUE (email)
);

CREATE TABLE organization_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    role VARCHAR(20) NOT NULL,

    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_memberships_org_user
        UNIQUE (organization_id, user_id),

    CONSTRAINT fk_organization_memberships_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id),

    CONSTRAINT fk_organization_memberships_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_organization_memberships_user
    ON organization_memberships (user_id);

CREATE INDEX idx_organization_memberships_organization
    ON organization_memberships (organization_id);