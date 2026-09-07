ALTER TABLE driver_assignments
    ADD COLUMN assignment_outcome VARCHAR(40) NOT NULL DEFAULT 'PENDING'
        AFTER assignment_source,
    ADD COLUMN outcome_reason VARCHAR(500) NULL
        AFTER assignment_outcome,
    ADD COLUMN outcome_reviewed_at DATETIME(6) NULL
        AFTER outcome_reason,
    ADD COLUMN outcome_reviewed_by_user_id BIGINT NULL
        AFTER outcome_reviewed_at;

ALTER TABLE driver_assignments
    ADD CONSTRAINT fk_driver_assignments_outcome_reviewed_by_user
        FOREIGN KEY (outcome_reviewed_by_user_id)
        REFERENCES users (id);

CREATE INDEX idx_driver_assignments_outcome
    ON driver_assignments (organization_id, assignment_outcome);

CREATE TABLE driving_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,

    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,

    started_by_user_id BIGINT NULL,
    ended_by_user_id BIGINT NULL,

    start_method VARCHAR(20) NOT NULL,
    end_method VARCHAR(20) NULL,

    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_driving_sessions_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id),

    CONSTRAINT fk_driving_sessions_assignment
        FOREIGN KEY (assignment_id)
        REFERENCES driver_assignments (id),

    CONSTRAINT fk_driving_sessions_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers (id),

    CONSTRAINT fk_driving_sessions_started_by_user
        FOREIGN KEY (started_by_user_id)
        REFERENCES users (id),

    CONSTRAINT fk_driving_sessions_ended_by_user
        FOREIGN KEY (ended_by_user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_driving_sessions_org_active
    ON driving_sessions (organization_id, ended_at, started_at);

CREATE INDEX idx_driving_sessions_assignment_started
    ON driving_sessions (assignment_id, started_at);

CREATE INDEX idx_driving_sessions_driver_started
    ON driving_sessions (driver_id, started_at);

/*
 * Preserve every legacy start/stop record before RoutePulse begins using
 * driving_sessions as the source of truth for actual driving.
 */
INSERT INTO driving_sessions (
    organization_id,
    assignment_id,
    driver_id,
    started_at,
    ended_at,
    started_by_user_id,
    ended_by_user_id,
    start_method,
    end_method,
    created_at
)
SELECT
    organization_id,
    id,
    driver_id,
    actual_started_at,
    actual_ended_at,
    actual_started_by_user_id,
    actual_ended_by_user_id,
    'LEGACY',
    CASE
        WHEN actual_ended_at IS NULL THEN NULL
        ELSE 'LEGACY'
    END,
    COALESCE(actual_started_at, created_at)
FROM driver_assignments
WHERE actual_started_at IS NOT NULL;

UPDATE driver_assignments
SET assignment_outcome = CASE
    WHEN actual_started_at IS NULL THEN 'PENDING'
    WHEN actual_ended_at IS NULL THEN 'IN_PROGRESS'
    WHEN change_reason = 'Driver self-override recorded from shuttle QR scan.'
         AND originally_suggested_driver_id IS NOT NULL
         AND originally_suggested_driver_id <> driver_id
        THEN 'COVERED_BY_OTHER_DRIVER'
    ELSE 'COMPLETED_AS_ASSIGNED'
END;

/*
 * From V26 forward the assignment column has one meaning only: the driver
 * RoutePulse/management planned to drive. The old driver_id column used to
 * be overwritten by QR takeovers, so first we copied its historical value
 * into driving_sessions above. Now rename it to make the new meaning explicit.
 */
ALTER TABLE driver_assignments
    RENAME COLUMN driver_id TO scheduled_driver_id;

/*
 * Recover the original scheduled driver for legacy QR self-overrides when
 * the old schema retained enough information to do so. This is exact for
 * normal AUTO assignments and for MANUAL assignments because the old QR
 * flow populated originally_suggested_driver_id before overwriting driver_id.
 */
UPDATE driver_assignments
SET scheduled_driver_id = originally_suggested_driver_id
WHERE change_reason = 'Driver self-override recorded from shuttle QR scan.'
  AND originally_suggested_driver_id IS NOT NULL
  AND originally_suggested_driver_id <> scheduled_driver_id;

CREATE TABLE driver_workload_events (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    session_id BIGINT NULL,

    event_type VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NULL,

    recorded_by_user_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_driver_workload_events_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id),

    CONSTRAINT fk_driver_workload_events_driver
        FOREIGN KEY (driver_id)
        REFERENCES drivers (id),

    CONSTRAINT fk_driver_workload_events_assignment
        FOREIGN KEY (assignment_id)
        REFERENCES driver_assignments (id),

    CONSTRAINT fk_driver_workload_events_session
        FOREIGN KEY (session_id)
        REFERENCES driving_sessions (id),

    CONSTRAINT fk_driver_workload_events_recorded_by_user
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id)
);

CREATE INDEX idx_driver_workload_events_org_created
    ON driver_workload_events (organization_id, created_at);

CREATE INDEX idx_driver_workload_events_driver_created
    ON driver_workload_events (driver_id, created_at);

CREATE INDEX idx_driver_workload_events_assignment_type
    ON driver_workload_events (assignment_id, event_type);

CREATE INDEX idx_driver_workload_events_session_type
    ON driver_workload_events (session_id, event_type);
