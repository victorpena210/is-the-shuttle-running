CREATE TABLE park_operating_days (
    id BIGINT NOT NULL AUTO_INCREMENT,
    park_id INT NOT NULL,
    operating_date DATE NOT NULL,
    operating_status VARCHAR(16) NOT NULL,
    opening_time TIME NULL,
    closing_time TIME NULL,
    buyout BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(80) NOT NULL,
    fetched_at DATETIME(6) NOT NULL,
    confirmed_by VARCHAR(255) NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_park_operating_days_park_date
        UNIQUE (park_id, operating_date),
    INDEX idx_park_operating_days_date (operating_date)
);
