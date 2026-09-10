package technology.renatus.routepulse.driving.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.operations.domain.OperationalStatusOverride;
import technology.renatus.routepulse.operations.domain.RiderOperationalStatus;
import technology.renatus.routepulse.operations.model.OperationalStatusUpdate;
import technology.renatus.routepulse.operations.service.OperationalStatusService;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class RiderStatusService {

    private final OrganizationRepository organizationRepository;
    private final DrivingSessionRepository drivingSessionRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final OperationalStatusService operationalStatusService;
    private final ZoneId operationsZone;
    private final long startGraceMinutes;
    private final long endingSoonMinutes;

    public RiderStatusService(
            OrganizationRepository organizationRepository,
            DrivingSessionRepository drivingSessionRepository,
            DriverAssignmentRepository driverAssignmentRepository,
            OperationalStatusService operationalStatusService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone,
            @Value("${app.attention.start-grace-minutes:10}") long startGraceMinutes,
            @Value("${app.rider-status.ending-soon-minutes:15}") long endingSoonMinutes) {

        this.organizationRepository = organizationRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.driverAssignmentRepository = driverAssignmentRepository;
        this.operationalStatusService = operationalStatusService;
        this.operationsZone = ZoneId.of(operationsZone);
        this.startGraceMinutes = Math.max(0, startGraceMinutes);
        this.endingSoonMinutes = Math.max(0, endingSoonMinutes);
    }

    public RiderStatus getStatus(String organizationSlug) {
        String slug = normalizeSlug(organizationSlug);
        Instant eventNow = Instant.now();
        LocalDateTime now = LocalDateTime.ofInstant(eventNow, operationsZone);

        Organization organization = organizationRepository
                .findBySlug(slug)
                .orElseThrow(() -> new RiderStatusNotFoundException(
                        "Rider status page was not found."));

        if (!organization.hasApplicationAccess(eventNow)) {
            throw new RiderStatusNotFoundException("Rider status page was not found.");
        }

        DrivingSession activeSession = drivingSessionRepository
                .findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(
                        organization.getId())
                .orElse(null);

        OperationalStatusUpdate latestUpdate = operationalStatusService
                .findLatest(organization.getId())
                .orElse(null);

        RiderOperationalStatus effectiveStatus;
        String operationalMessage = latestUpdate == null
                ? null
                : latestUpdate.getOperationalMessage();
        boolean automatic = latestUpdate == null
                || latestUpdate.getStatusOverride() == OperationalStatusOverride.AUTOMATIC;

        if (!automatic) {
            effectiveStatus = mapOverride(latestUpdate.getStatusOverride());
        } else {
            effectiveStatus = inferAutomaticStatus(
                    organization.getId(),
                    activeSession,
                    now);
        }

        String driverName = activeSession == null
                ? null
                : activeSession.getDriver().getName();

        boolean confirmationAvailable = activeSession != null
                && (effectiveStatus == RiderOperationalStatus.RUNNING
                    || effectiveStatus == RiderOperationalStatus.SERVICE_ENDING_SOON);

        return new RiderStatus(
                organization.getDisplayName(),
                effectiveStatus,
                headline(effectiveStatus),
                defaultDetail(effectiveStatus),
                operationalMessage,
                driverName,
                confirmationAvailable,
                tone(effectiveStatus));
    }

    private RiderOperationalStatus inferAutomaticStatus(
            Long organizationId,
            DrivingSession activeSession,
            LocalDateTime now) {

        if (activeSession != null) {
            LocalDateTime scheduledEnd = scheduledEndForCurrentWindow(
                    activeSession.getAssignment(),
                    now
            );
            if (scheduledEnd != null
                    && endingSoonMinutes > 0
                    && !now.isAfter(scheduledEnd)
                    && !now.isBefore(scheduledEnd.minusMinutes(endingSoonMinutes))) {
                return RiderOperationalStatus.SERVICE_ENDING_SOON;
            }
            return RiderOperationalStatus.RUNNING;
        }

        LocalDate today = now.toLocalDate();
        List<DriverAssignment> assignments = driverAssignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, today);

        if (assignments.isEmpty()) {
            return RiderOperationalStatus.NO_SERVICE_SCHEDULED;
        }

        boolean delayed = assignments.stream().anyMatch(assignment -> {
            if (assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED
                    || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED
                    || assignment.getAssignmentOutcome() == AssignmentOutcome.MISSED) {
                return false;
            }

            if (assignment.hasPlannedDrivingWindows()) {
                return assignment.getPlannedDrivingWindows().stream().anyMatch(window -> {
                    LocalDateTime start = assignment.getAssignmentDate().atTime(window.getStartTime());
                    LocalDateTime end = assignment.getAssignmentDate().atTime(window.getEndTime());
                    LocalDateTime delayedAt = start.plusMinutes(startGraceMinutes);
                    return !now.isBefore(delayedAt) && now.isBefore(end);
                });
            }

            if (assignment.getAssignmentOutcome() != AssignmentOutcome.PENDING
                    || assignment.hasDrivingSessions()) {
                return false;
            }

            LocalDateTime start = scheduledStart(assignment);
            LocalDateTime end = scheduledEnd(assignment);
            if (start == null || end == null) {
                return false;
            }
            LocalDateTime delayedAt = start.plusMinutes(startGraceMinutes);
            return !now.isBefore(delayedAt) && now.isBefore(end);
        });

        return delayed
                ? RiderOperationalStatus.DELAYED
                : RiderOperationalStatus.NOT_RUNNING;
    }

    private LocalDateTime scheduledEndForCurrentWindow(
            DriverAssignment assignment,
            LocalDateTime now) {

        if (assignment.hasPlannedDrivingWindows()) {
            PlannedDrivingWindow activeWindow = assignment.getPlannedDrivingWindows().stream()
                    .filter(window -> window.contains(now.toLocalTime()))
                    .findFirst()
                    .orElse(null);

            return activeWindow == null
                    ? null
                    : assignment.getAssignmentDate().atTime(activeWindow.getEndTime());
        }

        return scheduledEnd(assignment);
    }

    private LocalDateTime scheduledStart(DriverAssignment assignment) {
        LocalTime start = assignment.getScheduledStartTime();
        return start == null ? null : assignment.getAssignmentDate().atTime(start);
    }

    private LocalDateTime scheduledEnd(DriverAssignment assignment) {
        LocalTime end = assignment.getScheduledEndTime();
        if (end == null) {
            return null;
        }
        LocalDateTime result = assignment.getAssignmentDate().atTime(end);
        LocalTime start = assignment.getScheduledStartTime();
        if (start != null && end.isBefore(start)) {
            result = result.plusDays(1);
        }
        return result;
    }

    private RiderOperationalStatus mapOverride(OperationalStatusOverride override) {
        return switch (override) {
            case TEMPORARILY_PAUSED -> RiderOperationalStatus.TEMPORARILY_PAUSED;
            case DELAYED -> RiderOperationalStatus.DELAYED;
            case NO_SERVICE_SCHEDULED -> RiderOperationalStatus.NO_SERVICE_SCHEDULED;
            case SERVICE_ENDING_SOON -> RiderOperationalStatus.SERVICE_ENDING_SOON;
            case AUTOMATIC -> throw new IllegalArgumentException(
                    "Automatic status must be inferred, not mapped.");
        };
    }

    private String headline(RiderOperationalStatus status) {
        return switch (status) {
            case RUNNING -> "The shuttle is running.";
            case TEMPORARILY_PAUSED -> "Shuttle service is temporarily paused.";
            case DELAYED -> "Shuttle service is delayed.";
            case NO_SERVICE_SCHEDULED -> "No shuttle service is scheduled today.";
            case SERVICE_ENDING_SOON -> "Shuttle service is ending soon.";
            case NOT_RUNNING -> "The shuttle is not running right now.";
        };
    }

    private String defaultDetail(RiderOperationalStatus status) {
        return switch (status) {
            case RUNNING -> "Service is currently active.";
            case TEMPORARILY_PAUSED -> "Service is expected to resume when the operational issue is resolved.";
            case DELAYED -> "The planned shuttle start has passed, but service has not started yet.";
            case NO_SERVICE_SCHEDULED -> "RoutePulse does not have a shuttle driving assignment scheduled for today.";
            case SERVICE_ENDING_SOON -> "The current shuttle window is approaching its scheduled end.";
            case NOT_RUNNING -> "Check this page again when the next shuttle window begins.";
        };
    }

    private String tone(RiderOperationalStatus status) {
        return switch (status) {
            case RUNNING -> "running";
            case SERVICE_ENDING_SOON -> "ending";
            case TEMPORARILY_PAUSED -> "paused";
            case DELAYED -> "delayed";
            case NO_SERVICE_SCHEDULED -> "scheduled-off";
            case NOT_RUNNING -> "stopped";
        };
    }

    private String normalizeSlug(String organizationSlug) {
        if (organizationSlug == null || organizationSlug.isBlank()) {
            throw new RiderStatusNotFoundException("Rider status page was not found.");
        }
        return organizationSlug.trim().toLowerCase(Locale.ROOT);
    }

    public record RiderStatus(
            String organizationName,
            RiderOperationalStatus operationalStatus,
            String headline,
            String detail,
            String operationalMessage,
            String driverName,
            boolean confirmationAvailable,
            String tone) {

        public boolean running() {
            return operationalStatus == RiderOperationalStatus.RUNNING
                    || operationalStatus == RiderOperationalStatus.SERVICE_ENDING_SOON;
        }
    }

    public static class RiderStatusNotFoundException extends RuntimeException {
        public RiderStatusNotFoundException(String message) { super(message); }
    }
}
