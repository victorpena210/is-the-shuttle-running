CREATE TABLE uploaded_shifts (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,

    employee_id VARCHAR(50) NOT NULL,
    employee_name VARCHAR(200) NOT NULL,

    department VARCHAR(200) NOT NULL,
    job VARCHAR(200) NOT NULL,

    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,

    pickup_location VARCHAR(200) NOT NULL,

    imported_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_uploaded_shifts_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_uploaded_shifts_organization_date
    ON uploaded_shifts (
        organization_id,
        shift_date
    );

CREATE INDEX idx_uploaded_shifts_organization_employee
    ON uploaded_shifts (
        organization_id,
        employee_id
    );