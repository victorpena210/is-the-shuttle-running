CREATE TABLE pickup_riders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    location_override VARCHAR(200) NOT NULL DEFAULT '',
    active BIT NOT NULL DEFAULT 1,
    encrypted_link VARCHAR(2048) NOT NULL,
    feed_fingerprint VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    last_attempt_at DATETIME(6) NOT NULL,
    last_synced_at DATETIME(6) NOT NULL,
    next_sync_at DATETIME(6) NOT NULL,
    last_error VARCHAR(300),
    CONSTRAINT uk_pickup_feed_org UNIQUE (organization_id, feed_fingerprint),
    CONSTRAINT fk_pickup_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
    INDEX idx_pickup_due (active, next_sync_at)
);
CREATE TABLE pickup_shifts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rider_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    location VARCHAR(200) NOT NULL,
    CONSTRAINT fk_pickup_shift_rider FOREIGN KEY (rider_id) REFERENCES pickup_riders(id) ON DELETE CASCADE,
    INDEX idx_pickup_shift_end (rider_id, ends_at)
);
