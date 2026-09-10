CREATE TABLE historical_demand_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    source_schedule_import_id BIGINT NULL,

    operating_date DATE NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,

    shift_row_count INT NOT NULL,
    employee_count INT NOT NULL,

    morning_demand_count INT NOT NULL,
    midday_demand_count INT NOT NULL,
    evening_demand_count INT NOT NULL,
    off_hours_movement_count INT NOT NULL,

    morning_recommended_start TIME NULL,
    morning_recommended_end TIME NULL,

    overall_peak_window_start TIME NULL,
    overall_peak_window_end TIME NULL,
    overall_peak_window_count INT NOT NULL,
    overall_peak_period_title VARCHAR(80) NULL,

    snapshot_created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_historical_demand_org_date
        UNIQUE (organization_id, operating_date),

    CONSTRAINT fk_historical_demand_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_historical_demand_schedule_import
        FOREIGN KEY (source_schedule_import_id)
        REFERENCES schedule_imports (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_historical_demand_org_date
    ON historical_demand_snapshots (
        organization_id,
        operating_date
    );

CREATE INDEX idx_historical_demand_date
    ON historical_demand_snapshots (operating_date);

CREATE TABLE historical_demand_windows (
    id BIGINT NOT NULL AUTO_INCREMENT,

    snapshot_id BIGINT NOT NULL,
    window_start TIME NOT NULL,
    window_end TIME NOT NULL,
    arrivals INT NOT NULL,
    departures INT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_historical_demand_window
        UNIQUE (snapshot_id, window_start),

    CONSTRAINT fk_historical_demand_window_snapshot
        FOREIGN KEY (snapshot_id)
        REFERENCES historical_demand_snapshots (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_historical_demand_window_snapshot
    ON historical_demand_windows (snapshot_id);
