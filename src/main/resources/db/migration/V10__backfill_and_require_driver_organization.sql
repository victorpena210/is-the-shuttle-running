INSERT INTO organizations (
    legal_name,
    display_name,
    slug,
    status,
    trial_started_at,
    trial_ends_at,
    created_at,
    updated_at
)
SELECT
    'Six Flags Fiesta Texas',
    'Six Flags Fiesta Texas',
    'six-flags-fiesta-texas',
    'TRIAL',
    CURRENT_TIMESTAMP(6),
    DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 90 DAY),
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
WHERE EXISTS (
    SELECT 1
    FROM drivers
    WHERE organization_id IS NULL
)
AND NOT EXISTS (
    SELECT 1
    FROM organizations
    WHERE slug = 'six-flags-fiesta-texas'
);

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