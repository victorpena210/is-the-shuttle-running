package technology.renatus.routepulse.driving.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.DriverWorkloadService;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

@Service
public class DrivingSessionService {

    private final DriverAssignmentRepository assignmentRepository;
    private final DrivingSessionRepository drivingSessionRepository;
    private final DriverWorkloadService driverWorkloadService;

    public DrivingSessionService(
            DriverAssignmentRepository assignmentRepository,
            DrivingSessionRepository drivingSessionRepository,
            DriverWorkloadService driverWorkloadService) {

        this.assignmentRepository = assignmentRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.driverWorkloadService = driverWorkloadService;
    }

    @Transactional
    public DrivingSession startSession(
            Long organizationId,
            Long assignmentId,
            Driver actualDriver,
            UserAccount actor,
            LocalDateTime startedAt,
            DrivingActionMethod method) {

        DriverAssignment assignment = assignmentRepository
                .findByIdAndOrganizationForUpdate(
                        assignmentId,
                        organizationId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Driver assignment was not found."
                ));

        return startSession(
                assignment,
                actualDriver,
                actor,
                startedAt,
                method
        );
    }

    @Transactional
    public DrivingSession startSession(
            DriverAssignment assignment,
            Driver actualDriver,
            UserAccount actor,
            LocalDateTime startedAt,
            DrivingActionMethod method) {

        Objects.requireNonNull(assignment, "Assignment is required.");
        Objects.requireNonNull(actualDriver, "Actual driver is required.");
        Objects.requireNonNull(actor, "Account is required.");
        Objects.requireNonNull(startedAt, "Start time is required.");
        Objects.requireNonNull(method, "Start method is required.");

        if (assignment.getScheduledStartTime() == null
                || assignment.getScheduledEndTime() == null) {
            throw new IllegalStateException(
                    "Set the scheduled start and end time before starting driving."
            );
        }

        if (!actualDriver.isActive()) {
            throw new IllegalStateException(
                    "The driver must be active before driving can be recorded."
            );
        }

        if (!Objects.equals(
                assignment.getOrganization().getId(),
                actualDriver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The actual driver must belong to the assignment organization."
            );
        }

        if (assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED) {
            throw new IllegalStateException(
                    "This assignment has been closed by management and cannot be started."
            );
        }

        List<DrivingSession> activeSessions = drivingSessionRepository
                .findActiveForUpdateByOrganization(
                        assignment.getOrganization().getId()
                );

        if (!activeSessions.isEmpty()) {
            DrivingSession active = activeSessions.get(0);

            if (Objects.equals(
                    active.getAssignment().getId(),
                    assignment.getId()
            )) {
                throw new IllegalStateException(
                        "Driving has already been started for this assignment."
                );
            }

            throw new IllegalStateException(
                    "Another driving session is already active for this organization."
            );
        }

        DrivingSession session = DrivingSession.start(
                assignment,
                actualDriver,
                startedAt,
                actor,
                method
        );

        drivingSessionRepository.save(session);
        assignment.registerDrivingSession(session);
        assignment.markDrivingInProgress();

        return session;
    }

    @Transactional
    public DrivingSession takeOverActiveSession(
            DrivingSession activeSession,
            Driver newDriver,
            UserAccount actor,
            LocalDateTime changedAt,
            DrivingActionMethod method) {

        Objects.requireNonNull(activeSession, "Active driving session is required.");
        Objects.requireNonNull(newDriver, "New actual driver is required.");
        Objects.requireNonNull(actor, "Account is required.");
        Objects.requireNonNull(changedAt, "Takeover time is required.");
        Objects.requireNonNull(method, "Driving action method is required.");

        if (!activeSession.isActive()) {
            throw new IllegalStateException(
                    "The driving session being replaced is no longer active."
            );
        }

        DriverAssignment assignment = activeSession.getAssignment();

        if (!Objects.equals(
                assignment.getOrganization().getId(),
                newDriver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The new actual driver must belong to the assignment organization."
            );
        }

        if (!newDriver.isActive()) {
            throw new IllegalStateException(
                    "The driver must be active before driving can be recorded."
            );
        }

        if (Objects.equals(
                activeSession.getDriver().getId(),
                newDriver.getId()
        )) {
            throw new IllegalStateException(
                    "This driver already has the active driving session."
            );
        }

        /*
         * Close the previous driver's real-world session at the exact takeover
         * time, then open a second session on the SAME assignment. The planned
         * scheduled driver on DriverAssignment is never rewritten.
         */
        activeSession.end(
                changedAt,
                actor,
                method
        );

        updateDerivedOutcome(assignment);
        driverWorkloadService.recordSessionCompletion(
                activeSession,
                actor,
                changedAt
        );

        DrivingSession replacement = DrivingSession.start(
                assignment,
                newDriver,
                changedAt,
                actor,
                method
        );

        drivingSessionRepository.save(replacement);
        assignment.registerDrivingSession(replacement);
        assignment.markDrivingInProgress();

        return replacement;
    }

    @Transactional
    public DrivingSession endActiveSessionForAssignment(
            Long organizationId,
            Long assignmentId,
            UserAccount actor,
            LocalDateTime endedAt,
            DrivingActionMethod method) {

        DriverAssignment assignment = assignmentRepository
                .findByIdAndOrganizationForUpdate(
                        assignmentId,
                        organizationId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Driver assignment was not found."
                ));

        DrivingSession session = drivingSessionRepository
                .findFirstByAssignment_IdAndEndedAtIsNullOrderByStartedAtDesc(
                        assignment.getId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Start driving before ending the assignment."
                ));

        return endSession(
                assignment,
                session,
                actor,
                endedAt,
                method
        );
    }

    @Transactional
    public DrivingSession endSession(
            DriverAssignment assignment,
            DrivingSession session,
            UserAccount actor,
            LocalDateTime endedAt,
            DrivingActionMethod method) {

        Objects.requireNonNull(assignment, "Assignment is required.");
        Objects.requireNonNull(session, "Driving session is required.");

        if (!Objects.equals(
                assignment.getId(),
                session.getAssignment().getId()
        )) {
            throw new IllegalArgumentException(
                    "Driving session does not belong to this assignment."
            );
        }

        session.end(
                endedAt,
                actor,
                method
        );

        updateDerivedOutcome(assignment);
        driverWorkloadService.recordSessionCompletion(
                session,
                actor,
                endedAt
        );

        return session;
    }

    @Transactional(readOnly = true)
    public DrivingSession findActiveSession(Long organizationId) {
        return drivingSessionRepository
                .findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(
                        organizationId
                )
                .orElse(null);
    }

    @Transactional
    public DrivingSession findActiveSessionForUpdate(Long organizationId) {
        List<DrivingSession> active = drivingSessionRepository
                .findActiveForUpdateByOrganization(organizationId);

        if (active.size() > 1) {
            throw new IllegalStateException(
                    "More than one driving session is active. A manager must review the driving records."
            );
        }

        return active.isEmpty() ? null : active.get(0);
    }

    private void updateDerivedOutcome(DriverAssignment assignment) {
        List<DrivingSession> sessions = assignment.getDrivingSessions();

        if (sessions.isEmpty()) {
            assignment.updateDerivedOutcome(AssignmentOutcome.PENDING);
            return;
        }

        if (sessions.stream().anyMatch(DrivingSession::isActive)) {
            assignment.updateDerivedOutcome(AssignmentOutcome.IN_PROGRESS);
            return;
        }

        Long scheduledDriverId = assignment.getScheduledDriver().getId();

        boolean scheduledDriverParticipated = sessions.stream()
                .anyMatch(session -> Objects.equals(
                        session.getDriver().getId(),
                        scheduledDriverId
                ));

        boolean anotherDriverParticipated = sessions.stream()
                .anyMatch(session -> !Objects.equals(
                        session.getDriver().getId(),
                        scheduledDriverId
                ));

        if (scheduledDriverParticipated && anotherDriverParticipated) {
            assignment.updateDerivedOutcome(
                    AssignmentOutcome.PARTIALLY_COVERED
            );
        } else if (anotherDriverParticipated) {
            assignment.updateDerivedOutcome(
                    AssignmentOutcome.COVERED_BY_OTHER_DRIVER
            );
        } else {
            assignment.updateDerivedOutcome(
                    AssignmentOutcome.COMPLETED_AS_ASSIGNED
            );
        }
    }
}
