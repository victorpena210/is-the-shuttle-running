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

/** Selects the most appropriate startable or resumable assignment for a driver's physical checkpoint action. */
@Component
public class DriverScanAssignmentSelector {

    public DriverAssignment selectStartAssignment(
            Driver driver,
            List<DriverAssignment> assignments,
            LocalDateTime now) {

        List<DriverAssignment> candidates = assignments.stream()
                .filter(assignment -> !assignment.isDriving())
                .filter(assignment -> !isManagementClosed(assignment))
                .filter(assignment -> isStartableOrResumable(
                        assignment,
                        now.toLocalTime()))
                .toList();

        if (candidates.isEmpty()) {
            if (assignments.isEmpty()) {
                throw new NoStartableAssignmentException(
                        "There are no driving assignments scheduled for today.");
            }
            throw new NoStartableAssignmentException(
                    "There is no startable or resumable driving assignment right now. A manager can reopen an ended assignment if service needs to continue outside its scheduled window.");
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return candidates.stream()
                .filter(this::hasScheduledTimes)
                .min(Comparator
                        .<DriverAssignment>comparingInt(assignment ->
                                scanPriority(driver, assignment))
                        .thenComparingLong(assignment ->
                                distanceFromScheduledStart(
                                        now.toLocalTime(),
                                        relevantScheduledStart(assignment, now.toLocalTime())))
                        .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                .orElseThrow(() -> new IllegalStateException(
                        "Is the Shuttle Running cannot determine which shift this scan belongs to "
                                + "because today's remaining assignments do not have "
                                + "scheduled times. A manager must set the scheduled times first."));
    }

    public static class NoStartableAssignmentException extends IllegalStateException {
        public NoStartableAssignmentException(String message) { super(message); }
    }

    public void requireScheduledTimes(DriverAssignment assignment) {
        if (!hasScheduledTimes(assignment)) {
            throw new IllegalStateException(
                    "A manager must set the scheduled start and end time "
                            + "before this assignment can be started.");
        }
    }

    private boolean isStartableOrResumable(
            DriverAssignment assignment,
            LocalTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            /*
             * Detailed plans may contain a protected break or several midday
             * windows. Keep the assignment selectable before the next planned
             * window so DriverCheckpointStartPolicy can give the driver the exact
             * resume/start time, but drop it after all windows have ended.
             */
            return assignment.hasRemainingPlannedDrivingWindowAtOrAfter(now);
        }

        if (!assignment.hasDrivingSessions()) {
            return true;
        }

        /*
         * A checkpoint END closes only the current actual DrivingSession. It does not
         * permanently close the scheduled DriverAssignment. While the current
         * time is still inside that assignment's scheduled driving window, the
         * assignment remains eligible for a checkpoint resume. Outside the window, a
         * manager can still explicitly reopen it from coverage controls.
         */
        return hasScheduledTimes(assignment)
                && isInsideScheduledWindow(assignment, now);
    }

    private int scanPriority(
            Driver driver,
            DriverAssignment assignment) {

        boolean resuming = assignment.hasDrivingSessions();

        if (resuming && sameDriver(assignment.getFinalDriver(), driver)) {
            return 0;
        }

        if (resuming && sameDriver(assignment.getScheduledDriver(), driver)) {
            return 1;
        }

        if (!resuming && sameDriver(assignment.getScheduledDriver(), driver)) {
            return 2;
        }

        if (resuming) {
            return 3;
        }

        return 4;
    }

    private boolean hasScheduledTimes(DriverAssignment assignment) {
        return assignment.getScheduledStartTime() != null
                && assignment.getScheduledEndTime() != null;
    }

    private boolean isInsideScheduledWindow(
            DriverAssignment assignment,
            LocalTime now) {

        LocalTime start = assignment.getScheduledStartTime();
        LocalTime end = assignment.getScheduledEndTime();

        return start != null
                && end != null
                && !now.isBefore(start)
                && !now.isAfter(end);
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

    private LocalTime relevantScheduledStart(
            DriverAssignment assignment,
            LocalTime now) {
        if (!assignment.hasPlannedDrivingWindows()) {
            return assignment.getScheduledStartTime();
        }

        return assignment.getPlannedDrivingWindows().stream()
                .filter(window -> window.contains(now) || window.getStartTime().isAfter(now))
                .map(window -> window.contains(now) ? now : window.getStartTime())
                .findFirst()
                .orElse(assignment.getScheduledStartTime());
    }

    private long distanceFromScheduledStart(LocalTime now, LocalTime scheduledStartTime) {
        return Math.abs(Duration.between(now, scheduledStartTime).toMinutes());
    }
}
