CREATE TABLE driving_coverage_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_coverage_reviews_session (session_id, id),
    CONSTRAINT fk_coverage_review_session FOREIGN KEY (session_id) REFERENCES driving_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_coverage_review_actor FOREIGN KEY (reviewer_id) REFERENCES users(id)
);
