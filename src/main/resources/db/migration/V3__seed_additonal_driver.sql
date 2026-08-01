INSERT INTO drivers (employee_id, name, active)
SELECT '9000003', 'Demo Driver 3', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '9000003'
);