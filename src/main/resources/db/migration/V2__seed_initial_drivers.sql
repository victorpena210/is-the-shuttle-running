INSERT INTO drivers (employee_id, name, active)
SELECT '9000002', 'Demo Driver 2', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '9000002'
);

INSERT INTO drivers (employee_id, name, active)
SELECT '9000004', 'Demo Driver 4', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '9000004'
);

INSERT INTO drivers (employee_id, name, active)
SELECT '9000001', 'Demo Driver 1', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '9000001'
);