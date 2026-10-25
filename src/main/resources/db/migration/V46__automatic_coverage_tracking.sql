ALTER TABLE driving_sessions
    ADD COLUMN coverage_plan_captured BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve the plan independently of mutable automatic assignments. Existing
-- records have no reliable start-time plan and receive no inferred coverage.
CREATE TABLE driving_coverage_windows (
    session_id BIGINT NOT NULL,
    window_order INTEGER NOT NULL,
    service_date DATE NOT NULL,
    service_key VARCHAR(40) NOT NULL,
    scheduled_driver_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id, window_order),
    CONSTRAINT fk_coverage_window_session FOREIGN KEY (session_id)
        REFERENCES driving_sessions(id) ON DELETE CASCADE
);
