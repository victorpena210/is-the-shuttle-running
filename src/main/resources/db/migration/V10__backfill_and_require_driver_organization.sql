UPDATE drivers AS d
JOIN organizations AS o
    ON o.slug = 'six-flags-fiesta-texas'
SET d.organization_id = o.id
WHERE d.organization_id IS NULL;

ALTER TABLE drivers
    MODIFY COLUMN organization_id BIGINT NOT NULL,

    DROP INDEX uk_drivers_employee_id,

    ADD CONSTRAINT uk_drivers_organization_employee_id
        UNIQUE (organization_id, employee_id),

    ADD CONSTRAINT fk_drivers_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id);
