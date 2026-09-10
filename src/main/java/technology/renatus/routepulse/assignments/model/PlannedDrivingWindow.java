package technology.renatus.routepulse.assignments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One planned start/stop interval inside a broader driver assignment.
 *
 * Morning and afternoon assignments normally contain one continuous interval,
 * but RoutePulse can split that interval around a protected 15-minute break.
 * Midday turnover can contain several smaller intervals because demand is
 * intentionally sporadic.
 */
@Embeddable
public class PlannedDrivingWindow {

    @Column(name = "window_start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "window_end_time", nullable = false)
    private LocalTime endTime;

    protected PlannedDrivingWindow() {
    }

    public PlannedDrivingWindow(LocalTime startTime, LocalTime endTime) {
        this.startTime = Objects.requireNonNull(startTime, "Planned driving start time is required.");
        this.endTime = Objects.requireNonNull(endTime, "Planned driving end time is required.");

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "A planned driving window must end after it starts."
            );
        }
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public long getMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }

    public boolean contains(LocalTime time) {
        return time != null
                && !time.isBefore(startTime)
                && time.isBefore(endTime);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlannedDrivingWindow that)) {
            return false;
        }
        return Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }
}
