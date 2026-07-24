CREATE TABLE drivers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT uk_drivers_employee_id UNIQUE (employee_id)
);