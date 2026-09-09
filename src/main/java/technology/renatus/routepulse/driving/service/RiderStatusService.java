package technology.renatus.routepulse.driving.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class RiderStatusService {

    private final OrganizationRepository organizationRepository;
    private final DrivingSessionRepository drivingSessionRepository;
    private final ZoneId operationsZone;

    public RiderStatusService(
            OrganizationRepository organizationRepository,
            DrivingSessionRepository drivingSessionRepository,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.organizationRepository = organizationRepository;
        this.drivingSessionRepository = drivingSessionRepository;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public RiderStatus getStatus(String organizationSlug) {
        String slug = normalizeSlug(organizationSlug);

        Organization organization = organizationRepository
                .findBySlug(slug)
                .orElseThrow(() -> new RiderStatusNotFoundException(
                        "Rider status page was not found."
                ));

        if (!organization.hasApplicationAccess(
                LocalDateTime.now(operationsZone))) {
            throw new RiderStatusNotFoundException(
                    "Rider status page was not found."
            );
        }

        DrivingSession activeSession = drivingSessionRepository
                .findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(
                        organization.getId()
                )
                .orElse(null);

        if (activeSession == null) {
            return new RiderStatus(
                    organization.getDisplayName(),
                    false,
                    null
            );
        }

        return new RiderStatus(
                organization.getDisplayName(),
                true,
                activeSession.getDriver().getName()
        );
    }

    private String normalizeSlug(String organizationSlug) {
        if (organizationSlug == null || organizationSlug.isBlank()) {
            throw new RiderStatusNotFoundException(
                    "Rider status page was not found."
            );
        }

        return organizationSlug.trim().toLowerCase(Locale.ROOT);
    }

    public record RiderStatus(
            String organizationName,
            boolean running,
            String driverName) {
    }

    public static class RiderStatusNotFoundException
            extends RuntimeException {

        public RiderStatusNotFoundException(String message) {
            super(message);
        }
    }
}
