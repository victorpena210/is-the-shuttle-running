package technology.renatus.routepulse.driving.service.rider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.driving.model.DrivingSession;

/** Refreshing a page or submitting a passenger ride never confirms service. */
@Component
public class ServiceFreshnessPolicy {
    private final long confirmationMinutes;
    private final long overrunMinutes;
    private final ZoneId zone;

    public ServiceFreshnessPolicy(
            @Value("${app.operations-zone:America/Chicago}") String zone,
            @Value("${app.rider-status.confirmation-minutes:120}") long confirmationMinutes,
            @Value("${app.rider-status.overrun-grace-minutes:15}") long overrunMinutes) {
        this.zone = ZoneId.of(zone);
        this.confirmationMinutes = Math.max(15, Math.min(720, confirmationMinutes));
        this.overrunMinutes = Math.max(0, Math.min(120, overrunMinutes));
    }

    public Instant confirmationDueAt(DrivingSession session) {
        Instant confirmed = session.getLastConfirmedAt();
        Instant deadline = confirmed.plusSeconds(confirmationMinutes * 60);
        var a = session.getAssignment();
        if (a == null) return movementDeadline(session, deadline);
        // Use the window in which this session started, never a later window or another day.
        var start = LocalDateTime.ofInstant(session.getStartedAt(), zone);
        LocalDateTime plannedEnd = null;
        if (a.hasPlannedDrivingWindows()) {
            for (var w : a.getPlannedDrivingWindows()) {
                var from = a.getAssignmentDate().atTime(w.getStartTime());
                var to = a.getAssignmentDate().atTime(w.getEndTime());
                if (!to.isAfter(from)) to = to.plusDays(1);
                if (start.isBefore(to)) { plannedEnd = to; break; }
            }
        } else if (a.getScheduledStartTime() != null && a.getScheduledEndTime() != null) {
            plannedEnd = a.getAssignmentDate().atTime(a.getScheduledEndTime());
            if (!a.getScheduledEndTime().isAfter(a.getScheduledStartTime())) plannedEnd = plannedEnd.plusDays(1);
        }
        if (plannedEnd != null && confirmed.isBefore(plannedEnd.atZone(zone).toInstant())) {
            Instant endDeadline = plannedEnd.plusMinutes(overrunMinutes).atZone(zone).toInstant();
            if (endDeadline.isBefore(deadline)) deadline = endDeadline;
        }
        return movementDeadline(session, deadline);
    }

    private Instant movementDeadline(DrivingSession session, Instant deadline) {
        var movement = session.getMovement();
        if (!movement.enabled()) return deadline;
        Instant evidenceDeadline = movement.evidenceDueAt();
        return evidenceDeadline != null && evidenceDeadline.isBefore(deadline) ? evidenceDeadline : deadline;
    }

    public boolean isFresh(DrivingSession session, Instant now) {
        return session != null && session.isActive() && !now.isBefore(session.getLastConfirmedAt())
                && now.isBefore(confirmationDueAt(session))
                && (!session.getMovement().enabled() || session.getMovement().fresh(now));
    }
}
