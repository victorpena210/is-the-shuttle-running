package technology.renatus.routepulse.driving.movement;

import java.time.Instant;

/** Device time is used only to reject stale/replayed fixes; deadlines use server time. */
public record LocationSample(double latitude, double longitude, double accuracy, Double speed, Instant capturedAt) {
    public void validate(Instant now) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(accuracy) || accuracy <= 0 || accuracy > 100000
                || (speed != null && (!Double.isFinite(speed) || speed < 0))
                || capturedAt == null || capturedAt.isAfter(now.plusSeconds(5))
                || capturedAt.isBefore(now.minusSeconds(45)))
            throw new IllegalArgumentException("A current, valid location fix is required. Check the device clock and location permission.");
    }
}
