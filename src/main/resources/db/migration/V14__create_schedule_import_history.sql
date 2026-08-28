CREATE TABLE schedule_imports (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,

    version_id CHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,

    row_count INT NOT NULL,
    first_shift_date DATE NOT NULL,
    last_shift_date DATE NOT NULL,
    dates_represented VARCHAR(2000) NOT NULL,

    uploaded_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_schedule_imports_version_id
        UNIQUE (version_id),

    CONSTRAINT fk_schedule_imports_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_schedule_imports_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_schedule_imports_organization_uploaded_at
    ON schedule_imports (
        organization_id,
        uploaded_at
    );

ALTER TABLE uploaded_shifts
    ADD COLUMN schedule_import_id BIGINT NULL
        AFTER organization_id;

ALTER TABLE uploaded_shifts
    ADD CONSTRAINT fk_uploaded_shifts_schedule_import
        FOREIGN KEY (schedule_import_id)
        REFERENCES schedule_imports (id)
        ON DELETE SET NULL;

CREATE INDEX idx_uploaded_shifts_schedule_import
    ON uploaded_shifts (schedule_import_id);