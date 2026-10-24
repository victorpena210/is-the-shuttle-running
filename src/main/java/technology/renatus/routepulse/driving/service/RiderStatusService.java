package technology.renatus.routepulse.driving.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final OperationalStatusService operationalStatusService;
    private final technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness;
    private final ZoneId operationsZone;

    public RiderStatusService(
            OrganizationRepository organizationRepository,
            DrivingSessionRepository drivingSessionRepository,
            OperationalStatusService operationalStatusService,
            technology.renatus.routepulse.driving.service.rider.ServiceFreshnessPolicy freshness,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {

        this.organizationRepository = organizationRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.operationalStatusService = operationalStatusService;
        this.freshness = freshness;
        this.operationsZone = ZoneId.of(operationsZone);
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
                .filter(session -> !session.hasReachedDurationLimit(eventNow))
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
            effectiveStatus = inferAutomaticStatus(activeSession);
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
                List.of(),
                now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)),
                operationsZone.getId(), lastConfirmed, eventNow,
                activeSession == null ? null : freshness.confirmationDueAt(activeSession),
                activeSession == null ? null : activeSession.getId(),
                organization.getRiderPickupInstructions(), organization.getRiderHelpContact());
    }

    public record RiderScheduleWindow(String driverName, LocalDateTime start,
            String timeRange, String duration, String label, String tone) {}

    private RiderOperationalStatus inferAutomaticStatus(
            DrivingSession activeSession) {

        return activeSession == null ? RiderOperationalStatus.NOT_RUNNING : RiderOperationalStatus.RUNNING;
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
            case NO_SERVICE_SCHEDULED -> "Shuttle service is unavailable.";
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
            case DELAYED -> "A manager has reported a service delay.";
            case NO_SERVICE_SCHEDULED -> "A manager has reported that shuttle service is unavailable.";
            case SERVICE_ENDING_SOON -> "A manager has reported that service is ending soon.";
            case NOT_RUNNING -> "No driver is currently recording an active shuttle run. Check back for an update.";
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
