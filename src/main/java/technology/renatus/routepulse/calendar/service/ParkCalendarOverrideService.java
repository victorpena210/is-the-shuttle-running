package technology.renatus.routepulse.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.model.ParkCalendarOverride;
import technology.renatus.routepulse.calendar.repository.ParkCalendarOverrideRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

/** Creates and reads organization-scoped operating-calendar override audit records. */
@Service
@Transactional(readOnly = true)
public class ParkCalendarOverrideService {

    private static final Set<OrganizationRole> AUTHORIZED_ROLES =
            EnumSet.of(
                    OrganizationRole.OWNER,
                    OrganizationRole.ADMIN,
                    OrganizationRole.DISPATCHER);

    private final ParkCalendarOverrideRepository overrideRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final ZoneId operationsZone;

    public ParkCalendarOverrideService(
            ParkCalendarOverrideRepository overrideRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository membershipRepository,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.overrideRepository = overrideRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public Optional<ParkCalendarOverride> findLatest(
            Long organizationId,
            LocalDate operatingDate) {
        if (organizationId == null || operatingDate == null) {
            return Optional.empty();
        }
        return overrideRepository
                .findFirstByOrganizationIdAndOperatingDateOrderByConfirmedAtDescIdDesc(
                        organizationId,
                        operatingDate);
    }

    @Transactional
    public ParkCalendarOverride confirmParkOpen(
            Long organizationId,
            ParkOperatingDay observedCalendarDay,
            String actorEmail,
            String reason) {

        if (organizationId == null) {
            throw new IllegalArgumentException("An organization is required.");
        }
        if (observedCalendarDay == null || observedCalendarDay.date() == null) {
            throw new IllegalArgumentException("A calendar date is required.");
        }
        if (observedCalendarDay.status() == ParkOperatingStatus.OPEN) {
            throw new IllegalStateException(
                    "The park calendar already reports this date as open; an override is not needed.");
        }
        if (observedCalendarDay.status() != ParkOperatingStatus.UNKNOWN
                && observedCalendarDay.status() != ParkOperatingStatus.CLOSED) {
            throw new IllegalStateException("This calendar result cannot be overridden.");
        }

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for a park calendar override.");
        }
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("The override reason must be 500 characters or fewer.");
        }

        UserAccount actor = userAccountRepository
                .findByEmailIgnoreCase(actorEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in manager account could not be found."));

        OrganizationMembership membership = membershipRepository
                .findByOrganization_IdAndUser_Id(organizationId, actor.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in account does not belong to this organization."));

        if (!AUTHORIZED_ROLES.contains(membership.getRole())) {
            throw new IllegalStateException(
                    "Only an owner, admin, or dispatcher can override the park calendar.");
        }

        String displayName = (actor.getFirstName() + " " + actor.getLastName()).trim();
        if (displayName.isBlank()) {
            displayName = actor.getEmail();
        }

        ParkCalendarOverride override = new ParkCalendarOverride(
                organizationId,
                observedCalendarDay.date(),
                observedCalendarDay.status(),
                ParkOperatingStatus.OPEN,
                observedCalendarDay.verificationSource(),
                observedCalendarDay.diagnosticCode(),
                normalizedReason,
                actor.getId(),
                displayName,
                actor.getEmail(),
                LocalDateTime.now(operationsZone));

        return overrideRepository.save(override);
    }
}
