-- Current anonymous forecast, split by work date and actual event date.
-- Replacing a work date also replaces its following-day departures atomically.
CREATE TABLE transportation_demand_windows (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    source_import_id BIGINT NOT NULL,
    source_date DATE NOT NULL,
    event_date DATE NOT NULL,
    period VARCHAR(20) NOT NULL,
    window_start TIME(6) NOT NULL,
    clock_ins INT NOT NULL,
    clock_outs INT NOT NULL,
    CONSTRAINT fk_demand_window_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_demand_window_import FOREIGN KEY (source_import_id) REFERENCES schedule_imports(id),
    CONSTRAINT uk_demand_window UNIQUE (organization_id, source_date, event_date, period, window_start),
    INDEX idx_demand_calendar (organization_id, event_date),
    INDEX idx_demand_retention (source_date)
);
