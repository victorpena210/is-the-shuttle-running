CREATE TABLE driver_calendar_connections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    encrypted_link VARCHAR(2048) NOT NULL,
    feed_fingerprint VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    connected_at DATETIME(6) NOT NULL,
    last_attempt_at DATETIME(6) NOT NULL,
    last_synced_at DATETIME(6) NOT NULL,
    next_sync_at DATETIME(6) NOT NULL,
    coverage_from DATE NOT NULL,
    coverage_until DATE NOT NULL,
    last_error VARCHAR(300),
    CONSTRAINT uk_driver_calendar UNIQUE (driver_id),
    CONSTRAINT uk_calendar_feed_org UNIQUE (organization_id, feed_fingerprint),
    CONSTRAINT fk_calendar_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_calendar_driver FOREIGN KEY (driver_id) REFERENCES drivers(id),
    INDEX idx_calendar_due (next_sync_at)
);
CREATE TABLE driver_calendar_shifts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_calendar_shift_connection FOREIGN KEY (connection_id) REFERENCES driver_calendar_connections(id) ON DELETE CASCADE,
    INDEX idx_calendar_shift_window (connection_id, starts_at, ends_at)
);
