package technology.renatus.routepulse.driving.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** A recorded drive, without an invented scheduled driver or scheduled hours. */
public record UnscheduledDrivingRecord(Long id, String driverName,
        ZonedDateTime startedAt, ZonedDateTime endedAt, String startMethod,
        String endMethod, String status, long actualMinutes, boolean active) {
    public static UnscheduledDrivingRecord from(DrivingSession session, ZoneId zone) {
        return new UnscheduledDrivingRecord(session.getId(), session.getDriver().getName(),
                session.getStartedAt().atZone(zone),
                session.getEndedAt() == null ? null : session.getEndedAt().atZone(zone),
                session.getStartMethod().name(),
                session.getEndMethod() == null ? null : session.getEndMethod().name(),
                session.isActive() ? "Driving now"
                        : session.getEndReason() == null ? "Ended"
                        : switch (session.getEndReason()) {
                            case PAUSE -> "Paused";
                            case HANDOFF -> "Handed off";
                            case COMPLETED -> "Ended";
                        },
                session.getActualMinutes(), session.isActive());
    }
}
