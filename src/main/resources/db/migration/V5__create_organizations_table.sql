CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT,

    legal_name VARCHAR(200) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    slug VARCHAR(120) NOT NULL,

    status VARCHAR(20) NOT NULL,

    trial_started_at DATETIME(6) NULL,
    trial_ends_at DATETIME(6) NULL,

    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_organizations_slug
        UNIQUE (slug)
);

CREATE INDEX idx_organizations_status
    ON organizations (status);