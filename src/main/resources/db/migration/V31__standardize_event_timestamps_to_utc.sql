-- RoutePulse now maps real-world event/audit timestamps to java.time.Instant.
-- DATETIME(6) remains the physical MySQL type, while Hibernate reads/writes it
-- through UTC (hibernate.jdbc.time_zone=UTC).
--
-- Only columns that were previously written explicitly in app.operations-zone
-- are normalized here. Columns that used plain LocalDateTime.now() are left as
-- stored because production JVMs commonly already wrote those values in UTC.
-- This avoids double-shifting mixed legacy data.
--
-- CONVERT_TZ is preferred when MySQL timezone tables are available. The +05:00
-- fallback matches America/Chicago during this migration's September cutover.

UPDATE driver_assignments
SET schedule_reviewed_at = CASE
        WHEN schedule_reviewed_at IS NULL THEN NULL
        ELSE COALESCE(
            CONVERT_TZ(schedule_reviewed_at, 'America/Chicago', '+00:00'),
            DATE_ADD(schedule_reviewed_at, INTERVAL 5 HOUR)
        )
    END,
    outcome_reviewed_at = CASE
        WHEN outcome_reviewed_at IS NULL THEN NULL
        ELSE COALESCE(
            CONVERT_TZ(outcome_reviewed_at, 'America/Chicago', '+00:00'),
            DATE_ADD(outcome_reviewed_at, INTERVAL 5 HOUR)
        )
    END;

UPDATE driving_sessions
SET started_at = COALESCE(
        CONVERT_TZ(started_at, 'America/Chicago', '+00:00'),
        DATE_ADD(started_at, INTERVAL 5 HOUR)
    ),
    ended_at = CASE
        WHEN ended_at IS NULL THEN NULL
        ELSE COALESCE(
            CONVERT_TZ(ended_at, 'America/Chicago', '+00:00'),
            DATE_ADD(ended_at, INTERVAL 5 HOUR)
        )
    END,
    created_at = COALESCE(
        CONVERT_TZ(created_at, 'America/Chicago', '+00:00'),
        DATE_ADD(created_at, INTERVAL 5 HOUR)
    );

UPDATE driver_workload_events
SET created_at = COALESCE(
        CONVERT_TZ(created_at, 'America/Chicago', '+00:00'),
        DATE_ADD(created_at, INTERVAL 5 HOUR)
    );

UPDATE passenger_ride_confirmations
SET ride_window_start = COALESCE(
        CONVERT_TZ(ride_window_start, 'America/Chicago', '+00:00'),
        DATE_ADD(ride_window_start, INTERVAL 5 HOUR)
    ),
    submitted_at = COALESCE(
        CONVERT_TZ(submitted_at, 'America/Chicago', '+00:00'),
        DATE_ADD(submitted_at, INTERVAL 5 HOUR)
    );

UPDATE park_calendar_overrides
SET confirmed_at = COALESCE(
        CONVERT_TZ(confirmed_at, 'America/Chicago', '+00:00'),
        DATE_ADD(confirmed_at, INTERVAL 5 HOUR)
    );

UPDATE operational_status_updates
SET updated_at = COALESCE(
        CONVERT_TZ(updated_at, 'America/Chicago', '+00:00'),
        DATE_ADD(updated_at, INTERVAL 5 HOUR)
    );

UPDATE historical_demand_snapshots
SET snapshot_created_at = COALESCE(
        CONVERT_TZ(snapshot_created_at, 'America/Chicago', '+00:00'),
        DATE_ADD(snapshot_created_at, INTERVAL 5 HOUR)
    );
