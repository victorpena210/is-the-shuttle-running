package technology.renatus.routepulse.driver.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointAction;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointPreview;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointResult;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.model.UnscheduledDrivingRecord;

/** Facade for the driver portal's profile, assignment history, and physical checkpoint actions. */
@Service
@Transactional(readOnly = true)
public class DriverPortalService {

    private final DriverPortalAccessService accessService;
    private final DriverCheckpointService checkpointService;
    private final DriverAssignmentRepository assignmentRepository;
    private final ZoneId operationsZone;
    private final DrivingSessionRepository drivingSessionRepository;
    private final technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness;

    public DriverPortalService(
            DriverPortalAccessService accessService,
            DriverCheckpointService checkpointService,
            DriverAssignmentRepository assignmentRepository,
            DrivingSessionRepository drivingSessionRepository,
            technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.drivingSessionRepository = drivingSessionRepository;
        this.freshness = freshness;
        this.accessService = accessService;
        this.checkpointService = checkpointService;
        this.assignmentRepository = assignmentRepository;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        return accessService.requireCurrentDriver(authentication);
    }

    public ActiveConfirmation getActiveConfirmation(Authentication authentication) {
        var driver = requireCurrentDriver(authentication);
        return drivingSessionRepository.findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(driver.getOrganization().getId())
                .filter(s -> s.getDriver().getId().equals(driver.getId()))
                .map(s -> new ActiveConfirmation(s.getId(), freshness.confirmationDueAt(s), !freshness.isFresh(s, java.time.Instant.now())))
                .orElse(null);
    }

    public record ActiveConfirmation(Long sessionId, java.time.Instant dueAt, boolean overdue) {}

    public List<UnscheduledDrivingRecord> getRecentUnscheduledRecords(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return drivingSessionRepository
                .findTop20ByOrganization_IdAndDriver_IdAndAssignmentIsNullOrderByStartedAtDesc(
                        driver.getOrganization().getId(), driver.getId())
                .stream().map(session -> UnscheduledDrivingRecord.from(session, operationsZone)).toList();
    }

    public List<DriverAssignment> getTodayAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return assignmentRepository
                .findAllVisibleToDriverOnDate(
                        driver.getOrganization().getId(),
                        driver.getId(),
                        currentOperationsDate())
                .stream()
                .sorted(Comparator.comparingInt(assignment -> assignment.getPeriod().ordinal()))
                .toList();
    }

    /**
     * Pick the assignment the driver should care about right now. Active driving
     * always wins; otherwise choose the earliest assignment with driving time
     * remaining today. This keeps the driver home screen action-first without
     * changing any assignment or checkpoint-session rules.
     */
    public DriverAssignment selectPrimaryTodayAssignment(
            List<DriverAssignment> assignments) {

        if (assignments == null || assignments.isEmpty()) {
            return null;
        }

        return assignments.stream()
                .filter(DriverAssignment::isDriving)
                .findFirst()
                .orElseGet(() -> {
                    LocalTime now = currentOperationsTime();

                    return assignments.stream()
                            .filter(assignment -> hasDrivingTimeRemaining(assignment, now))
                            .min(Comparator
                                    .comparing((DriverAssignment assignment) -> nextRelevantTime(assignment, now))
                                    .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                            .orElse(null);
                });
    }

    public List<DriverAssignment> getRecentAssignments(Authentication authentication) {
        Driver driver = requireCurrentDriver(authentication);
        return assignmentRepository
                .findAllVisibleToDriver(driver.getOrganization().getId(), driver.getId())
                .stream()
                .sorted(Comparator
                        .comparing(DriverAssignment::getAssignmentDate, Comparator.reverseOrder())
                        .thenComparingInt(assignment -> assignment.getPeriod().ordinal()))
                .limit(20)
                .toList();
    }

    public CheckpointPreview getCheckpointPreview(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method) {

        return checkpointService.getPreview(authentication, checkpointCode, method);
    }

    @Transactional
    public CheckpointResult recordCheckpoint(
            Authentication authentication,
            String checkpointCode,
            DrivingActionMethod method,
            CheckpointAction requestedAction) {

        return checkpointService.recordCheckpoint(
                authentication,
                checkpointCode,
                method,
                requestedAction);
    }

    public LocalDate currentOperationsDate() {
        return LocalDate.now(operationsZone);
    }

    public LocalTime currentOperationsTime() {
        return LocalTime.now(operationsZone);
    }

    private boolean hasDrivingTimeRemaining(
            DriverAssignment assignment,
            LocalTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            return assignment.hasRemainingPlannedDrivingWindowAtOrAfter(now);
        }

        if (assignment.getScheduledEndTime() != null) {
            return !now.isAfter(assignment.getScheduledEndTime());
        }

        return !assignment.isDrivingCompleted();
    }

    private LocalTime nextRelevantTime(
            DriverAssignment assignment,
            LocalTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            LocalTime nextStart = assignment.getNextPlannedDrivingStart(now);
            return nextStart == null ? LocalTime.MAX : nextStart;
        }

        LocalTime start = assignment.getScheduledStartTime();
        if (start == null || !now.isBefore(start)) {
            return now;
        }

        return start;
    }
}
