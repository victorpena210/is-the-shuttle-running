INSERT INTO drivers (employee_id, name, active)
SELECT '3665404', 'Victor Pena', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '3665404'
);

INSERT INTO drivers (employee_id, name, active)
SELECT '3683811', 'Jody Irons', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '3683811'
);

INSERT INTO drivers (employee_id, name, active)
SELECT '3621979', 'Jonathan Flores', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '3621979'
);