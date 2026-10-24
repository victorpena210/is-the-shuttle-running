package technology.renatus.routepulse.driving.service.rider;

import java.time.Instant;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.driving.model.DrivingSession;

/**
 * Service follows the recorded session lifecycle. Confirmation age, scheduled
 * end times and optional phone location do not expire an active session.
 * Every session has a four-hour maximum duration.
 * The existing API names are retained for the driver and rider status consumers.
 */
@Component
public class ServiceFreshnessPolicy {
    /** Active sessions have no confirmation deadline. */
    public Instant confirmationDueAt(DrivingSession session) {
        return null;
    }

    public boolean isFresh(DrivingSession session, Instant now) {
        return session != null && session.isActive() && !session.hasReachedDurationLimit(now);
    }
}
