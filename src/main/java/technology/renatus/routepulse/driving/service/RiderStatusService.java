package technology.renatus.routepulse.driving.service;

import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
import technology.renatus.routepulse.driving.service.rider.PublicDriverName;
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
    private final technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness;
    private final ZoneId operationsZone;
    private final long startGraceMinutes;
    private final long endingSoonMinutes;

    public RiderStatusService(
            OrganizationRepository organizationRepository,
            DrivingSessionRepository drivingSessionRepository,
            DriverAssignmentRepository driverAssignmentRepository,
            OperationalStatusService operationalStatusService,
            technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone,
            @Value("${app.attention.start-grace-minutes:10}") long startGraceMinutes,
            @Value("${app.rider-status.ending-soon-minutes:15}") long endingSoonMinutes) {

        this.organizationRepository = organizationRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.driverAssignmentRepository = driverAssignmentRepository;
        this.operationalStatusService = operationalStatusService;
        this.freshness = freshness;
        this.operationsZone = ZoneId.of(operationsZone);
        this.startGraceMinutes = Math.max(0, startGraceMinutes);
        this.endingSoonMinutes = Math.max(0, endingSoonMinutes);
    }

    public RiderStatus getStatus(String organizationSlug) {
        return getStatus(organizationSlug, Instant.now());
    }

    RiderStatus getStatus(String organizationSlug, Instant eventNow) {
        String slug = normalizeSlug(organizationSlug);
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

        DrivingSession lastEnded = activeSession == null
                ? drivingSessionRepository.findFirstByOrganization_IdAndEndedAtIsNotNullOrderByEndedAtDesc(organization.getId()).orElse(null)
                : null;
        boolean inactivityTimeout = lastEnded != null && lastEnded.getEndReason()
                == technology.renatus.routepulse.driving.domain.DrivingSessionEndReason.INACTIVITY_TIMEOUT;
        Instant lastConfirmed = activeSession != null ? activeSession.getLastConfirmedAt()
                : lastEnded == null ? null : inactivityTimeout ? lastEnded.getLastConfirmedAt() : lastEnded.getEndedAt();
        boolean manual = latestUpdate != null && latestUpdate.getStatusOverride() != OperationalStatusOverride.AUTOMATIC;
        boolean expired = latestUpdate != null && latestUpdate.isExpired(eventNow);
        RiderOperationalStatus effectiveStatus;
        // Temporary manager overrides return to the recorded session state on expiry.
        if (manual && !expired) {
            effectiveStatus = mapOverride(latestUpdate.getStatusOverride());
            if (effectiveStatus == RiderOperationalStatus.SERVICE_ENDING_SOON
                    && !freshness.isFresh(activeSession, eventNow))
                effectiveStatus = RiderOperationalStatus.CONFIRMATION_NEEDED;
        } else if (inactivityTimeout) {
            effectiveStatus = RiderOperationalStatus.CONFIRMATION_NEEDED;
        } else if (activeSession != null && activeSession.getMovement().waiting()) {
            effectiveStatus = RiderOperationalStatus.WAITING_AT_PICKUP;
        } else if (lastEnded != null && lastEnded.getEndReason() == technology.renatus.routepulse.driving.domain.DrivingSessionEndReason.PAUSE
                && lastEnded.getEndedAt().isAfter(eventNow.minusSeconds(7200))) {
            effectiveStatus = RiderOperationalStatus.TEMPORARILY_PAUSED;
        } else {
            effectiveStatus = inferAutomaticStatus(organization.getId(), activeSession, now);
        }
        if (manual && (lastConfirmed == null || latestUpdate.getUpdatedAt().isAfter(lastConfirmed)))
            lastConfirmed = latestUpdate.getUpdatedAt();
        String operationalMessage = latestUpdate == null || expired ? null : latestUpdate.getOperationalMessage();

        String driverName = activeSession == null
                ? null
                : PublicDriverName.firstName(activeSession.getDriver().getName());

        boolean confirmationAvailable = activeSession != null
                && (effectiveStatus == RiderOperationalStatus.RUNNING
                    || effectiveStatus == RiderOperationalStatus.SERVICE_ENDING_SOON
                    || effectiveStatus == RiderOperationalStatus.WAITING_AT_PICKUP);
        String detail = defaultDetail(effectiveStatus);
        if (effectiveStatus == RiderOperationalStatus.CONFIRMATION_NEEDED && inactivityTimeout)
            detail = "The last session was automatically closed after sustained inactivity and needs a service check. Contact your shuttle coordinator before waiting for a ride.";
        if (manual && !expired) {
            if (effectiveStatus == RiderOperationalStatus.DELAYED) detail = "A manager has reported a service delay. Check the operational update for details.";
            if (effectiveStatus == RiderOperationalStatus.NO_SERVICE_SCHEDULED) detail = "A manager has reported no service for this period.";
            if (effectiveStatus == RiderOperationalStatus.SERVICE_ENDING_SOON) detail = "A manager has reported that the current service is ending soon.";
        }

        return new RiderStatus(
                organization.getDisplayName(),
                effectiveStatus,
                headline(effectiveStatus),
                detail,
                operationalMessage,
                driverName,
                confirmationAvailable,
                tone(effectiveStatus),
                dailySchedule(organization.getId(), now),
                now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)),
                operationsZone.getId(), lastConfirmed, eventNow,
                activeSession == null ? null : freshness.confirmationDueAt(activeSession),
                activeSession == null ? null : activeSession.getId(),
                organization.getRiderPickupInstructions(), organization.getRiderHelpContact());
    }

    private List<RiderScheduleWindow> dailySchedule(Long organizationId, LocalDateTime now) {
        List<RiderScheduleWindow> result = new ArrayList<>();
        // Include an overnight window that began yesterday and still overlaps today.
        for (LocalDate date : List.of(now.toLocalDate().minusDays(1), now.toLocalDate())) {
            for (DriverAssignment assignment : driverAssignmentRepository
                    .findAllByOrganization_IdAndAssignmentDate(organizationId, date)) {
                if (assignment.hasPlannedDrivingWindows()) {
                    for (PlannedDrivingWindow window : assignment.getPlannedDrivingWindows()) {
                        addScheduleWindow(result, assignment, window.getStartTime(), window.getEndTime(), now);
                    }
                } else {
                    addScheduleWindow(result, assignment, assignment.getScheduledStartTime(),
                            assignment.getScheduledEndTime(), now);
                }
            }
        }
        result.sort(Comparator.comparing(RiderScheduleWindow::start));
        return List.copyOf(result);
    }

    private void addScheduleWindow(List<RiderScheduleWindow> result, DriverAssignment assignment,
            LocalTime startTime, LocalTime endTime, LocalDateTime now) {
        if (startTime == null || endTime == null) return;
        LocalDateTime start = assignment.getAssignmentDate().atTime(startTime);
        LocalDateTime end = assignment.getAssignmentDate().atTime(endTime);
        if (!end.isAfter(start)) end = end.plusDays(1);
        if (!start.isBefore(now.toLocalDate().plusDays(1).atStartOfDay())
                || !end.isAfter(now.toLocalDate().atStartOfDay())) return;
        String label;
        String tone;
        AssignmentOutcome outcome = assignment.getAssignmentOutcome();
        if (outcome == AssignmentOutcome.CANCELLED || outcome == AssignmentOutcome.EXCUSED) {
            label = "Cancelled"; tone = "cancelled";
        } else if (outcome == AssignmentOutcome.MISSED) {
            label = "Missed"; tone = "cancelled";
        } else if (!now.isBefore(end)) {
            label = "Scheduled window ended"; tone = "completed";
        } else if (now.isBefore(start)) {
            label = "Upcoming"; tone = "upcoming";
        } else {
            label = "Current scheduled window"; tone = "current";
        }
        long minutes = Duration.between(start, end).toMinutes();
        String duration = (minutes / 60 > 0 ? minutes / 60 + " hr" : "")
                + (minutes % 60 > 0 ? (minutes / 60 > 0 ? " " : "") + minutes % 60 + " min" : "");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        String range = start.format(time) + " – " + end.format(time);
        if (!start.toLocalDate().equals(now.toLocalDate())) range = "Yesterday " + range;
        if (end.toLocalDate().isAfter(now.toLocalDate())) range += " (next day)";
        result.add(new RiderScheduleWindow(
                assignment.getScheduledDriver() == null ? "Driver to be assigned"
                        : PublicDriverName.firstName(assignment.getScheduledDriver().getName()),
                start, range, duration, label, tone));
    }

    public record RiderScheduleWindow(String driverName, LocalDateTime start,
            String timeRange, String duration, String label, String tone) {}

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

        if (assignment == null) return null;
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
            case CONFIRMATION_NEEDED -> "Status unconfirmed.";
            case WAITING_AT_PICKUP -> "Waiting at pickup.";
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
            case WAITING_AT_PICKUP -> "The driver has confirmed they are waiting for passengers. Service remains active.";
            case CONFIRMATION_NEEDED -> "The latest service confirmation is overdue. We cannot confirm whether the shuttle is running. Check with your organization before waiting for a ride.";
            case TEMPORARILY_PAUSED -> "Service is expected to resume when the operational issue is resolved.";
            case DELAYED -> "Planned service is due, but no driving session is currently active.";
            case NO_SERVICE_SCHEDULED -> "Is the Shuttle Running does not have a shuttle driving assignment scheduled for today.";
            case SERVICE_ENDING_SOON -> "The current shuttle window is approaching its scheduled end.";
            case NOT_RUNNING -> "Check this page again when the next shuttle window begins.";
        };
    }

    private String tone(RiderOperationalStatus status) {
        return switch (status) {
            case RUNNING, WAITING_AT_PICKUP -> "running";
            case CONFIRMATION_NEEDED -> "unconfirmed";
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
            String tone,
            List<RiderScheduleWindow> schedule,
            String scheduleDate,
            String scheduleZone, Instant lastConfirmedAt, Instant pageRefreshedAt,
            Instant confirmationDueAt, Long activeSessionId,
            String pickupInstructions, String helpContact) {

        public RiderStatus(String organizationName, RiderOperationalStatus operationalStatus, String headline,
                String detail, String operationalMessage, String driverName, boolean confirmationAvailable,
                String tone, List<RiderScheduleWindow> schedule, String scheduleDate, String scheduleZone) {
            this(organizationName, operationalStatus, headline, detail, operationalMessage, driverName,
                    confirmationAvailable, tone, schedule, scheduleDate, scheduleZone, null, null, null, null, null, null);
        }

        public RiderScheduleWindow nextPlannedWindow() {
            return schedule.stream().filter(w -> "current".equals(w.tone()) || "upcoming".equals(w.tone()))
                    .findFirst().orElse(null);
        }

        public boolean running() {
            return operationalStatus == RiderOperationalStatus.RUNNING
                    || operationalStatus == RiderOperationalStatus.SERVICE_ENDING_SOON
                    || operationalStatus == RiderOperationalStatus.WAITING_AT_PICKUP;
        }
    }

    public static class RiderStatusNotFoundException extends RuntimeException {
        public RiderStatusNotFoundException(String message) { super(message); }
    }
}
