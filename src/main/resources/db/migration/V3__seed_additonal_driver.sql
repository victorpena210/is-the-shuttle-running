INSERT INTO drivers (employee_id, name, active)
SELECT '3683722', 'Dorothy Hernandez', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM drivers
    WHERE employee_id = '3683722'
);