ALTER TABLE driver_assignments
    ADD COLUMN organization_id BIGINT NULL AFTER id;

UPDATE driver_assignments AS assignment
JOIN drivers AS driver
    ON driver.id = assignment.driver_id
SET assignment.organization_id = driver.organization_id
WHERE assignment.organization_id IS NULL;

ALTER TABLE driver_assignments
    MODIFY COLUMN organization_id BIGINT NOT NULL,

    DROP INDEX uk_driver_assignments_date_period,

    DROP INDEX uk_driver_assignments_date_driver,

    ADD CONSTRAINT uk_driver_assignments_org_date_period
        UNIQUE (
            organization_id,
            assignment_date,
            shuttle_period
        ),

    ADD CONSTRAINT uk_driver_assignments_org_date_driver
        UNIQUE (
            organization_id,
            assignment_date,
            driver_id
        ),

    ADD CONSTRAINT fk_driver_assignments_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id);