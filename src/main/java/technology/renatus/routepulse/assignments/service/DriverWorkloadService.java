package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.DriverWorkloadEventType;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.DriverWorkloadEvent;
import technology.renatus.routepulse.assignments.repository.DriverWorkloadEventRepository;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;

@Service
public class DriverWorkloadService {

    private final DrivingSessionRepository drivingSessionRepository;
    private final DriverWorkloadEventRepository workloadEventRepository;

    public DriverWorkloadService(
            DrivingSessionRepository drivingSessionRepository,
            DriverWorkloadEventRepository workloadEventRepository) {

        this.drivingSessionRepository = drivingSessionRepository;
        this.workloadEventRepository = workloadEventRepository;
    }

    @Transactional(readOnly = true)
    public List<DriverSchedule> rankEligibleDrivers(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverSchedule> eligibleDrivers) {

        if (eligibleDrivers == null || eligibleDrivers.isEmpty()) {
            return List.of();
        }

        Map<Long, WorkloadSnapshot> snapshots = buildSnapshots(
                organizationId,
                assignmentDate
        );

        return eligibleDrivers.stream()
                .sorted(
                        Comparator
                                .comparingInt(
                                        (DriverSchedule schedule) -> snapshotFor(
                                                snapshots,
                                                schedule.driverId()
                                        ).outstandingMakeups()
                                )
                                .reversed()
                                .thenComparingLong(
                                        schedule -> snapshotFor(
                                                snapshots,
                                                schedule.driverId()
                                        ).actualMinutes()
                                )
                                .thenComparing(
                                        schedule -> snapshotFor(
                                                snapshots,
                                                schedule.driverId()
                                        ).lastActualDrive(),
                                        Comparator.nullsFirst(
                                                Comparator.naturalOrder()
                                        )
                                )
                                .thenComparing(DriverSchedule::driverName)
                                .thenComparing(DriverSchedule::employeeId)
                )
                .toList();
    }

    @Transactional
    public void recordSessionCompletion(
            DrivingSession session,
            UserAccount actor,
            LocalDateTime recordedAt) {

        Objects.requireNonNull(session, "Driving session is required.");

        if (!session.isCompleted()) {
            throw new IllegalStateException(
                    "A workload completion event requires a completed driving session."
            );
        }

        Long sessionId = session.getId();

        if (sessionId != null
                && (workloadEventRepository.existsBySession_IdAndEventType(
                            sessionId,
                            DriverWorkloadEventType.DRIVE_COMPLETED
                    )
                    || workloadEventRepository.existsBySession_IdAndEventType(
                            sessionId,
                            DriverWorkloadEventType.EXTRA_COVERAGE
                    )
                    || workloadEventRepository.existsBySession_IdAndEventType(
                            sessionId,
                            DriverWorkloadEventType.MAKEUP_COMPLETED
                    ))) {
            return;
        }

        Driver actualDriver = session.getDriver();
        DriverAssignment currentAssignment = session.getAssignment();

        boolean droveOwnScheduledAssignment = Objects.equals(
                actualDriver.getId(),
                currentAssignment.getScheduledDriver().getId()
        );

        if (droveOwnScheduledAssignment) {
            DriverWorkloadEvent outstanding = findOldestOutstandingMakeup(
                    currentAssignment.getOrganization().getId(),
                    actualDriver.getId()
            );

            if (outstanding != null) {
                workloadEventRepository.save(
                        new DriverWorkloadEvent(
                                actualDriver,
                                outstanding.getAssignment(),
                                session,
                                DriverWorkloadEventType.MAKEUP_COMPLETED,
                                "Completed a later scheduled driving assignment.",
                                actor,
                                recordedAt
                        )
                );
                return;
            }

            workloadEventRepository.save(
                    new DriverWorkloadEvent(
                            actualDriver,
                            currentAssignment,
                            session,
                            DriverWorkloadEventType.DRIVE_COMPLETED,
                            "Completed scheduled driving duty.",
                            actor,
                            recordedAt
                    )
            );
            return;
        }

        workloadEventRepository.save(
                new DriverWorkloadEvent(
                        actualDriver,
                        currentAssignment,
                        session,
                        DriverWorkloadEventType.EXTRA_COVERAGE,
                        "Covered a driving assignment scheduled to another driver.",
                        actor,
                        recordedAt
                )
        );
    }

    @Transactional
    public void setMakeupRequired(
            DriverAssignment assignment,
            boolean makeupRequired,
            String reason,
            UserAccount actor,
            LocalDateTime recordedAt) {

        Objects.requireNonNull(assignment, "Assignment is required.");
        Objects.requireNonNull(actor, "Manager is required.");
        Objects.requireNonNull(recordedAt, "Recorded time is required.");

        Driver scheduledDriver = assignment.getScheduledDriver();
        boolean currentlyOutstanding = isMakeupOutstandingForAssignment(
                assignment.getOrganization().getId(),
                scheduledDriver.getId(),
                assignment.getId()
        );

        if (makeupRequired && !currentlyOutstanding) {
            workloadEventRepository.save(
                    new DriverWorkloadEvent(
                            scheduledDriver,
                            assignment,
                            null,
                            DriverWorkloadEventType.MAKEUP_REQUIRED,
                            reason,
                            actor,
                            recordedAt
                    )
            );
        } else if (!makeupRequired && currentlyOutstanding) {
            workloadEventRepository.save(
                    new DriverWorkloadEvent(
                            scheduledDriver,
                            assignment,
                            null,
                            DriverWorkloadEventType.MAKEUP_WAIVED,
                            reason == null || reason.isBlank()
                                    ? "Manager removed future make-up priority."
                                    : reason,
                            actor,
                            recordedAt
                    )
            );
        }
    }

    @Transactional(readOnly = true)
    public boolean isMakeupOutstandingForAssignment(
            Long organizationId,
            Long driverId,
            Long assignmentId) {

        Map<Long, DriverWorkloadEvent> outstanding = outstandingMakeups(
                workloadEventRepository
                        .findAllByOrganization_IdAndDriver_IdOrderByCreatedAtAsc(
                                organizationId,
                                driverId
                        )
        );

        return outstanding.containsKey(assignmentId);
    }

    @Transactional(readOnly = true)
    public WorkloadSnapshot getDriverSnapshot(
            Long organizationId,
            Long driverId,
            LocalDate beforeDate) {

        return snapshotFor(
                buildSnapshots(organizationId, beforeDate),
                driverId
        );
    }

    private Map<Long, WorkloadSnapshot> buildSnapshots(
            Long organizationId,
            LocalDate beforeDate) {

        List<DrivingSession> completedSessions = drivingSessionRepository
                .findAllByOrganization_IdAndEndedAtIsNotNullAndAssignment_AssignmentDateBefore(
                        organizationId,
                        beforeDate
                );

        Map<Long, Long> minutesByDriver = new HashMap<>();
        Map<Long, LocalDateTime> lastDriveByDriver = new HashMap<>();

        for (DrivingSession session : completedSessions) {
            Long driverId = session.getDriver().getId();

            minutesByDriver.merge(
                    driverId,
                    session.getActualMinutes(),
                    Long::sum
            );

            lastDriveByDriver.merge(
                    driverId,
                    session.getEndedAt(),
                    (first, second) -> first.isAfter(second) ? first : second
            );
        }

        List<DriverWorkloadEvent> events = workloadEventRepository
                .findAllByOrganization_IdAndCreatedAtBeforeOrderByCreatedAtAsc(
                        organizationId,
                        beforeDate.atStartOfDay()
                );

        Map<Long, Map<Long, DriverWorkloadEvent>> outstandingByDriver =
                new HashMap<>();

        for (DriverWorkloadEvent event : events) {
            Long driverId = event.getDriver().getId();
            Map<Long, DriverWorkloadEvent> driverOutstanding =
                    outstandingByDriver.computeIfAbsent(
                            driverId,
                            ignored -> new LinkedHashMap<>()
                    );

            applyMakeupEvent(driverOutstanding, event);
        }

        Map<Long, WorkloadSnapshot> result = new HashMap<>();
        List<Long> driverIds = new ArrayList<>();
        driverIds.addAll(minutesByDriver.keySet());
        driverIds.addAll(lastDriveByDriver.keySet());
        driverIds.addAll(outstandingByDriver.keySet());

        driverIds.stream().distinct().forEach(driverId ->
                result.put(
                        driverId,
                        new WorkloadSnapshot(
                                outstandingByDriver
                                        .getOrDefault(driverId, Map.of())
                                        .size(),
                                minutesByDriver.getOrDefault(driverId, 0L),
                                lastDriveByDriver.get(driverId)
                        )
                )
        );

        return result;
    }

    private DriverWorkloadEvent findOldestOutstandingMakeup(
            Long organizationId,
            Long driverId) {

        Map<Long, DriverWorkloadEvent> outstanding = outstandingMakeups(
                workloadEventRepository
                        .findAllByOrganization_IdAndDriver_IdOrderByCreatedAtAsc(
                                organizationId,
                                driverId
                        )
        );

        return outstanding.values().stream()
                .min(Comparator.comparing(DriverWorkloadEvent::getCreatedAt))
                .orElse(null);
    }

    private Map<Long, DriverWorkloadEvent> outstandingMakeups(
            List<DriverWorkloadEvent> events) {

        Map<Long, DriverWorkloadEvent> outstanding = new LinkedHashMap<>();

        for (DriverWorkloadEvent event : events) {
            applyMakeupEvent(outstanding, event);
        }

        return outstanding;
    }

    private void applyMakeupEvent(
            Map<Long, DriverWorkloadEvent> outstanding,
            DriverWorkloadEvent event) {

        Long assignmentId = event.getAssignment().getId();

        switch (event.getEventType()) {
            case MAKEUP_REQUIRED -> outstanding.put(assignmentId, event);
            case MAKEUP_COMPLETED, MAKEUP_WAIVED -> outstanding.remove(assignmentId);
            default -> {
                // Other workload events do not create or clear make-up priority.
            }
        }
    }

    private WorkloadSnapshot snapshotFor(
            Map<Long, WorkloadSnapshot> snapshots,
            Long driverId) {

        return snapshots.getOrDefault(
                driverId,
                new WorkloadSnapshot(0, 0L, null)
        );
    }

    public record WorkloadSnapshot(
            int outstandingMakeups,
            long actualMinutes,
            LocalDateTime lastActualDrive) {

        public double actualHours() {
            return actualMinutes / 60.0;
        }
    }
}
