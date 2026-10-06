package technology.renatus.routepulse.operations.service;

import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
@Transactional
public class ServiceConfirmationService {
    private static final Set<OrganizationRole> MANAGERS = Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.DISPATCHER);
    private final UserAccountRepository users;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final DrivingSessionService sessions;

    public ServiceConfirmationService(UserAccountRepository users, OrganizationMembershipRepository memberships,
            OrganizationRepository organizations, DrivingSessionService sessions) {
        this.users = users; this.memberships = memberships; this.organizations = organizations; this.sessions = sessions;
    }

    private UserAccount actor(Long organizationId, String email, boolean managerOnly) {
        var user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new IllegalStateException("Account was not found."));
        var membership = memberships.findByOrganization_IdAndUser_Id(organizationId, user.getId())
                .orElseThrow(() -> new IllegalStateException("You do not belong to this organization."));
        if (!MANAGERS.contains(membership.getRole()) && (managerOnly || membership.getRole() != OrganizationRole.DRIVER))
            throw new IllegalStateException("You cannot update shuttle operations.");
        return user;
    }

    private DrivingSession active(Long organizationId, Long expectedSessionId) {
        organizations.findByIdForUpdate(organizationId).orElseThrow(() -> new IllegalStateException("Organization was not found."));
        var session = sessions.findActiveSessionForUpdate(organizationId);
        if (session == null || !session.getId().equals(expectedSessionId))
            throw new IllegalStateException("The active driver changed. Refresh before confirming or closing service.");
        return session;
    }

    public void confirm(Long organizationId, Long sessionId, String email, boolean driverAction) {
        var user = actor(organizationId, email, !driverAction);
        var session = active(organizationId, sessionId);
        if (driverAction && (!session.getDriver().isActive() || session.getDriver().getUser() == null || !session.getDriver().getUser().getId().equals(user.getId())))
            throw new IllegalStateException("Only the current driver can confirm this session from the driver page.");
        session.confirmRunning(Instant.now(), user);
    }

    public void close(Long organizationId, Long sessionId, Instant endedAt, String email) {
        var user = actor(organizationId, email, true);
        var session = active(organizationId, sessionId);
        if (endedAt == null || endedAt.isAfter(Instant.now()) || endedAt.isBefore(session.getStartedAt()))
            throw new IllegalArgumentException("Choose the actual end time, between the session start and now.");
        sessions.endSession(session.getAssignment(), session, user, endedAt, DrivingActionMethod.MANAGER);
    }

    public void updateRiderInformation(Long organizationId, String pickup, String help, String email) {
        actor(organizationId, email, true);
        var org = organizations.findByIdForUpdate(organizationId).orElseThrow(() -> new IllegalStateException("Organization was not found."));
        org.updateRiderInformation(pickup, help);
    }
}
