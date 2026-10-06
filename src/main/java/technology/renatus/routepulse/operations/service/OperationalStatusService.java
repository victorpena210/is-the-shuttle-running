package technology.renatus.routepulse.operations.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.operations.domain.OperationalStatusOverride;
import technology.renatus.routepulse.operations.model.OperationalStatusUpdate;
import technology.renatus.routepulse.operations.repository.OperationalStatusUpdateRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;

@Service
@Transactional(readOnly = true)
public class OperationalStatusService {

    private static final Set<OrganizationRole> AUTHORIZED_ROLES = EnumSet.of(
            OrganizationRole.OWNER,
            OrganizationRole.ADMIN,
            OrganizationRole.DISPATCHER);

    private final OperationalStatusUpdateRepository updateRepository;
    private final UserAccountRepository userAccountRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public OperationalStatusService(
            OperationalStatusUpdateRepository updateRepository,
            UserAccountRepository userAccountRepository,
            OrganizationMembershipRepository membershipRepository) {
        this.updateRepository = updateRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
    }

    public Optional<OperationalStatusUpdate> findLatest(Long organizationId) {
        if (organizationId == null) {
            return Optional.empty();
        }
        return updateRepository.findFirstByOrganizationIdOrderByUpdatedAtDescIdDesc(organizationId);
    }


    @Transactional
    public OperationalStatusUpdate update(
            Long organizationId,
            OperationalStatusOverride statusOverride,
            String message,
            String actorEmail) {

        return update(organizationId, statusOverride, message, actorEmail, 120);
    }

    @Transactional
    public OperationalStatusUpdate update(Long organizationId, OperationalStatusOverride statusOverride,
            String message, String actorEmail, int durationMinutes) {
        if (durationMinutes < 15 || durationMinutes > 720)
            throw new IllegalArgumentException("Choose a status duration between 15 and 720 minutes.");

        if (organizationId == null) {
            throw new IllegalArgumentException("An organization is required.");
        }
        if (statusOverride == null) {
            throw new IllegalArgumentException("An operational status is required.");
        }

        String normalizedMessage = message == null ? null : message.trim();
        if (normalizedMessage != null && normalizedMessage.isBlank()) {
            normalizedMessage = null;
        }
        if (normalizedMessage != null && normalizedMessage.length() > 500) {
            throw new IllegalArgumentException("The operational message must be 500 characters or fewer.");
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
                    "Only an owner, admin, or dispatcher can update live shuttle status.");
        }

        String displayName = (actor.getFirstName() + " " + actor.getLastName()).trim();
        if (displayName.isBlank()) {
            displayName = actor.getEmail();
        }

        OperationalStatusUpdate update = new OperationalStatusUpdate(
                organizationId,
                statusOverride,
                normalizedMessage,
                actor.getId(),
                displayName,
                actor.getEmail(),
                Instant.now());

        update.setExpiresAt(update.getUpdatedAt().plusSeconds(durationMinutes * 60L));

        return updateRepository.save(update);
    }
}
