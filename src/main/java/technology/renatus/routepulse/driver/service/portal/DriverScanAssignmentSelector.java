package technology.renatus.routepulse.driver.service.portal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;

/** Selects the most appropriate remaining assignment for a driver's QR scan. */
@Component
public class DriverScanAssignmentSelector {

    public DriverAssignment selectStartAssignment(
            Driver driver,
            List<DriverAssignment> assignments,
            LocalDateTime now) {

        List<DriverAssignment> available = assignments.stream()
                .filter(assignment -> !assignment.isDriving())
                .filter(assignment -> !assignment.isDrivingCompleted())
                .filter(assignment -> !isManagementClosed(assignment))
                .toList();

        if (available.isEmpty()) {
            if (assignments.isEmpty()) {
                throw new IllegalStateException(
                        "There are no driving assignments scheduled for today.");
            }
            throw new IllegalStateException(
                    "All driving assignments for today are already complete or closed.");
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        return available.stream()
                .filter(this::hasScheduledTimes)
                .min(Comparator
                        .<DriverAssignment>comparingLong(assignment ->
                                distanceFromScheduledStart(
                                        now.toLocalTime(),
                                        assignment.getScheduledStartTime()))
                        .thenComparingInt(assignment ->
                                sameDriver(assignment.getScheduledDriver(), driver) ? 0 : 1)
                        .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                .orElseThrow(() -> new IllegalStateException(
                        "RoutePulse cannot determine which shift this scan belongs to "
                                + "because today's remaining assignments do not have "
                                + "scheduled times. A manager must set the scheduled times first."));
    }

    public void requireScheduledTimes(DriverAssignment assignment) {
        if (!hasScheduledTimes(assignment)) {
            throw new IllegalStateException(
                    "A manager must set the scheduled start and end time "
                            + "before this assignment can be started.");
        }
    }

    private boolean hasScheduledTimes(DriverAssignment assignment) {
        return assignment.getScheduledStartTime() != null
                && assignment.getScheduledEndTime() != null;
    }

    private boolean isManagementClosed(DriverAssignment assignment) {
        return assignment.getAssignmentOutcome() == AssignmentOutcome.MISSED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED;
    }

    private boolean sameDriver(Driver first, Driver second) {
        return first != null
                && second != null
                && Objects.equals(first.getId(), second.getId());
    }

    private long distanceFromScheduledStart(LocalTime now, LocalTime scheduledStartTime) {
        return Math.abs(Duration.between(now, scheduledStartTime).toMinutes());
    }
}
