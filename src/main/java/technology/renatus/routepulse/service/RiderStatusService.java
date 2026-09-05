package technology.renatus.routepulse.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class RiderStatusService {

    private final OrganizationRepository organizationRepository;
    private final DriverAssignmentRepository driverAssignmentRepository;
    private final ZoneId operationsZone;

    public RiderStatusService(
            OrganizationRepository organizationRepository,
            DriverAssignmentRepository driverAssignmentRepository,
            @Value("${app.operations-zone:America/Chicago}")
            String operationsZone) {

        this.organizationRepository = organizationRepository;
        this.driverAssignmentRepository = driverAssignmentRepository;
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

        DriverAssignment activeAssignment = driverAssignmentRepository
                .findFirstByOrganization_IdAndActualStartedAtIsNotNullAndActualEndedAtIsNullOrderByActualStartedAtDesc(
                        organization.getId()
                )
                .orElse(null);

        if (activeAssignment == null) {
            return new RiderStatus(
                    organization.getDisplayName(),
                    false,
                    null
            );
        }

        return new RiderStatus(
                organization.getDisplayName(),
                true,
                activeAssignment.getDriver().getName()
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