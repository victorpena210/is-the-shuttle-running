CREATE TABLE automatic_schedule_days (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(80) NOT NULL,
    park_opening TIME(6), park_closing TIME(6),
    wardrobe_opening TIME(6), wardrobe_closing TIME(6),
    refreshed_at DATETIME(6) NOT NULL,
    message VARCHAR(500) NOT NULL,
    CONSTRAINT uk_auto_schedule_org_date UNIQUE (organization_id, service_date),
    CONSTRAINT fk_auto_schedule_org FOREIGN KEY (organization_id) REFERENCES organizations(id)
);
CREATE TABLE automatic_driving_slots (
    schedule_day_id BIGINT NOT NULL,
    slot_order INTEGER NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    driver_id BIGINT,
    driver_name VARCHAR(255),
    reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (schedule_day_id, slot_order),
    CONSTRAINT fk_auto_slot_day FOREIGN KEY (schedule_day_id) REFERENCES automatic_schedule_days(id) ON DELETE CASCADE,
    INDEX ix_auto_slot_driver (driver_id, starts_at)
);
