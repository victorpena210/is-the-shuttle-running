package technology.renatus.routepulse.assignments.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;

/** Derived state calculated from an assignment's actual driving sessions. */
final class DriverAssignmentDrivingHistory {

    private final List<DrivingSession> sessions;
    private final Driver scheduledDriver;

    DriverAssignmentDrivingHistory(List<DrivingSession> sessions, Driver scheduledDriver) {
        this.sessions = sessions == null ? List.of() : sessions;
        this.scheduledDriver = scheduledDriver;
    }

    boolean hasSessions() {
        return !sessions.isEmpty();
    }

    Driver finalDriver() {
        return sessions.stream()
                .max(Comparator.comparing(DrivingSession::getStartedAt))
                .map(DrivingSession::getDriver)
                .orElse(scheduledDriver);
    }

    DrivingSession activeSession() {
        return sessions.stream()
                .filter(DrivingSession::isActive)
                .max(Comparator.comparing(DrivingSession::getStartedAt))
                .orElse(null);
    }

    LocalDateTime actualStartedAt() {
        return sessions.stream()
                .map(DrivingSession::getStartedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    UserAccount actualStartedBy() {
        return sessions.stream()
                .min(Comparator.comparing(DrivingSession::getStartedAt))
                .map(DrivingSession::getStartedBy)
                .orElse(null);
    }

    LocalDateTime actualEndedAt() {
        if (!hasSessions() || isDriving()) {
            return null;
        }
        return sessions.stream()
                .map(DrivingSession::getEndedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    UserAccount actualEndedBy() {
        if (!hasSessions() || isDriving()) {
            return null;
        }
        return sessions.stream()
                .filter(DrivingSession::isCompleted)
                .max(Comparator.comparing(DrivingSession::getEndedAt))
                .map(DrivingSession::getEndedBy)
                .orElse(null);
    }

    boolean isDriving() {
        return activeSession() != null;
    }

    boolean isCompleted() {
        return hasSessions() && !isDriving();
    }

    long actualMinutes() {
        return sessions.stream()
                .filter(DrivingSession::isCompleted)
                .mapToLong(DrivingSession::getActualMinutes)
                .sum();
    }

    String actualDriverNames() {
        if (!hasSessions()) {
            return "—";
        }
        return sessions.stream()
                .map(DrivingSession::getDriver)
                .filter(Objects::nonNull)
                .map(Driver::getName)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("—");
    }
}
