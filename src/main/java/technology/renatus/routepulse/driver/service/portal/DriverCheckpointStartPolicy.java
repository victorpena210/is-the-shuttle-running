package technology.renatus.routepulse.driver.service.portal;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.driver.model.Driver;

/** Protects scheduled drivers from accidentally starting outside their planned driving time. */
@Component
public class DriverCheckpointStartPolicy {

    private static final DateTimeFormatter DISPLAY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public void requireStartAllowed(
            Driver checkpointDriver,
            DriverAssignment assignment,
            LocalDateTime now) {

        Objects.requireNonNull(checkpointDriver, "Checkpoint driver is required.");
        Objects.requireNonNull(assignment, "Assignment is required.");
        Objects.requireNonNull(now, "Checkpoint time is required.");

        /*
         * A different eligible driver is an intentional fill-in/coverage action.
         * Managers also retain their explicit override path.
         */
        if (!sameDriver(assignment.getScheduledDriver(), checkpointDriver)) {
            return;
        }

        LocalTime currentTime = now.toLocalTime();

        if (assignment.hasPlannedDrivingWindows()) {
            requireInsideDetailedWindow(assignment, currentTime);
            return;
        }

        LocalTime scheduledStartTime = assignment.getScheduledStartTime();
        if (scheduledStartTime == null || !currentTime.isBefore(scheduledStartTime)) {
            return;
        }

        throw new IllegalStateException(
                "Your driving assignment begins at "
                        + format(scheduledStartTime)
                        + ". Start when you clock in.");
    }

    private void requireInsideDetailedWindow(
            DriverAssignment assignment,
            LocalTime currentTime) {

        if (assignment.isInsidePlannedDrivingWindow(currentTime)) {
            return;
        }

        PlannedDrivingWindow nextWindow = assignment.getPlannedDrivingWindows().stream()
                .filter(window -> window.getStartTime().isAfter(currentTime))
                .findFirst()
                .orElse(null);

        if (nextWindow == null) {
            throw new IllegalStateException(
                    "Your planned driving time for the "
                            + assignment.getPeriodLabel().toLowerCase(java.util.Locale.ROOT)
                            + " shift has ended. A manager can override this if service still needs coverage.");
        }

        PlannedDrivingWindow currentBreak = assignment.getPlannedBreakWindows().stream()
                .filter(window -> window.contains(currentTime))
                .findFirst()
                .orElse(null);

        if (assignment.getPeriod() != ShuttlePeriod.MIDDAY && currentBreak != null) {
            throw new IllegalStateException(
                    "This is your planned break. Resume driving at "
                            + format(nextWindow.getStartTime())
                            + ".");
        }

        if (assignment.getPeriod() == ShuttlePeriod.MIDDAY
                && assignment.getScheduledStartTime() != null
                && !currentTime.isBefore(assignment.getScheduledStartTime())) {
            throw new IllegalStateException(
                    "Midday driving is not scheduled right now. Your next driving window begins at "
                            + format(nextWindow.getStartTime())
                            + ".");
        }

        throw new IllegalStateException(
                "Your driving assignment begins at "
                        + format(nextWindow.getStartTime())
                        + ". Start when that driving window begins.");
    }

    private String periodLabel(ShuttlePeriod period) {
        return switch (period) {
            case MORNING -> "morning";
            case MIDDAY -> "midday";
            case AFTERNOON -> "afternoon";
        };
    }

    private String format(LocalTime time) {
        return time.format(DISPLAY_TIME_FORMAT);
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }
}
