ALTER TABLE driving_sessions
    ADD COLUMN duration_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN time_reviewed_at DATETIME(6) NULL,
    ADD COLUMN time_review_revision BIGINT NOT NULL DEFAULT 0;

-- Include runs capped by the previous release and older, unusually long records.
UPDATE driving_sessions SET duration_review_required = TRUE
WHERE end_reason = 'DURATION_LIMIT' OR ended_at >= DATE_ADD(started_at, INTERVAL 4 HOUR);
CREATE INDEX idx_session_duration_review ON driving_sessions
    (organization_id, duration_review_required, time_reviewed_at);

CREATE TABLE driving_session_time_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    action VARCHAR(20) NOT NULL,
    previous_start DATETIME(6) NOT NULL,
    previous_end DATETIME(6) NULL,
    corrected_start DATETIME(6) NOT NULL,
    corrected_end DATETIME(6) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_time_review_session FOREIGN KEY (session_id) REFERENCES driving_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_time_review_actor FOREIGN KEY (reviewer_id) REFERENCES users(id),
    INDEX idx_time_review_session (session_id, reviewed_at)
);
