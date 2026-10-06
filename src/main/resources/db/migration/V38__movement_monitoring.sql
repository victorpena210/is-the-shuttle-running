ALTER TABLE organizations ADD COLUMN movement_route VARCHAR(12000) NULL;
ALTER TABLE driving_sessions
    ADD COLUMN monitoring_started_at DATETIME(6) NULL,
    ADD COLUMN location_captured_at DATETIME(6) NULL,
    ADD COLUMN location_received_at DATETIME(6) NULL,
    ADD COLUMN location_reliable_at DATETIME(6) NULL,
    ADD COLUMN movement_confirmed_at DATETIME(6) NULL,
    ADD COLUMN stationary_since DATETIME(6) NULL,
    ADD COLUMN inactivity_prompted_at DATETIME(6) NULL,
    ADD COLUMN location_anchor_lat DOUBLE NULL,
    ADD COLUMN location_anchor_lon DOUBLE NULL,
    ADD COLUMN location_anchor_accuracy DOUBLE NULL,
    ADD COLUMN location_anchor_at DATETIME(6) NULL,
    ADD COLUMN location_last_lat DOUBLE NULL,
    ADD COLUMN location_last_lon DOUBLE NULL,
    ADD COLUMN location_last_accuracy DOUBLE NULL,
    ADD COLUMN movement_waiting BOOLEAN NULL,
    ADD COLUMN location_uncertain BOOLEAN NULL,
    ADD COLUMN inactivity_reviewed_at DATETIME(6) NULL,
    ADD COLUMN inactivity_reviewed_by BIGINT NULL,
    ADD COLUMN inactivity_review_note VARCHAR(1000) NULL,
    ADD CONSTRAINT fk_session_inactivity_reviewer FOREIGN KEY (inactivity_reviewed_by) REFERENCES users(id);
CREATE INDEX idx_session_inactivity_review ON driving_sessions (organization_id, end_reason, inactivity_reviewed_at);
