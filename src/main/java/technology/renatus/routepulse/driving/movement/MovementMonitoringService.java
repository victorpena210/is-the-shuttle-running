package technology.renatus.routepulse.driving.movement;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Service
@Transactional
public class MovementMonitoringService {
    public enum Response { WAITING, BREAK, END }
    private final DriverPortalAccessService access;
    private final CurrentOrganizationService current;
    private final OrganizationMembershipRepository memberships;
    private final DrivingSessionService sessions;
    private final DrivingSessionRepository repository;

    public MovementMonitoringService(DriverPortalAccessService access, CurrentOrganizationService current,
            OrganizationMembershipRepository memberships, DrivingSessionService sessions, DrivingSessionRepository repository) {
        this.access=access; this.current=current; this.memberships=memberships; this.sessions=sessions; this.repository=repository;
    }
    private DrivingSession owned(Authentication authentication, Long sessionId) {
        var driver=access.requireCurrentDriver(authentication);
        access.requireActiveDriver(driver);
        sessions.lockOrganization(driver.getOrganization().getId());
        var session=repository.findForReview(sessionId,driver.getOrganization().getId())
                .orElseThrow(() -> new IllegalStateException("This driving session was not found."));
        if (!Objects.equals(session.getDriver().getId(),driver.getId()))
            throw new org.springframework.security.access.AccessDeniedException("Only this session’s driver can send its location or respond.");
        return session;
    }
    private void requireActive(DrivingSession session) {
        if (!session.isActive()) throw new IllegalStateException("This session has ended. Refresh to see the latest status.");
    }
    public Snapshot status(Authentication auth, Long sessionId) { return snapshot(owned(auth,sessionId),Instant.now()); }
    public Snapshot enable(Authentication auth, Long sessionId) {
        var session=owned(auth,sessionId); requireActive(session);
        session.getMovement().start(Instant.now());
        return snapshot(session,Instant.now());
    }
    public Snapshot location(Authentication auth, Long sessionId, LocationSample sample) {
        var session=owned(auth,sessionId); requireActive(session);
        Instant now=Instant.now();
        if (sample.capturedAt()!=null && sample.capturedAt().isBefore(session.getStartedAt()))
            throw new IllegalArgumentException("The location must be from this driving session.");
        if (session.getMovement().accept(sample,ShuttleRoute.parse(session.getOrganization().getMovementRoute()),now))
            session.confirmMovement(now);
        evaluate(session,now);
        return snapshot(session,now);
    }
    public Snapshot signalLost(Authentication auth, Long sessionId) {
        var session=owned(auth,sessionId);
        if (session.isActive()) session.getMovement().loseSignal();
        return snapshot(session,Instant.now());
    }
    public Snapshot respond(Authentication auth, Long sessionId, Response response) {
        var session=owned(auth,sessionId); requireActive(session);
        UserAccount user=access.requireCurrentUser(auth);
        Instant now=Instant.now();
        if (response==Response.WAITING) {
            session.confirmRunning(now,user);
            session.getMovement().acknowledge(now,true);
        } else {
            sessions.endSession(session.getAssignment(),session,user,now,DrivingActionMethod.DRIVER,
                    response==Response.BREAK ? DrivingSessionEndReason.PAUSE : DrivingSessionEndReason.COMPLETED);
        }
        return snapshot(session,now);
    }
    /** Invoked through the Spring proxy by the job; serialized with starts, handoffs and responses. */
    public void evaluateOrganization(Long organizationId, Instant now) {
        sessions.lockOrganization(organizationId);
        var session=sessions.findActiveSessionForUpdate(organizationId);
        if (session!=null) evaluate(session,now);
    }
    void evaluate(DrivingSession session, Instant now) {
        session.getMovement().evaluate(now);
        if (session.isActive() && session.getMovement().timedOut(now)) {
            sessions.endSession(session.getAssignment(),session,null,now,DrivingActionMethod.AUTOMATIC,
                    DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        }
    }
    private UserAccount manager(Authentication auth, Long organizationId) {
        var user=access.requireCurrentUser(auth);
        var membership=memberships.findByOrganization_IdAndUser_Id(organizationId,user.getId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Organization access is required."));
        if (!Set.of(OrganizationRole.OWNER,OrganizationRole.ADMIN,OrganizationRole.DISPATCHER).contains(membership.getRole()))
            throw new org.springframework.security.access.AccessDeniedException("Manager access is required.");
        return user;
    }
    public void saveRoute(Authentication auth, String route) {
        Long organizationId=current.requireOrganization(auth).getId();
        manager(auth,organizationId);
        var organization=sessions.lockOrganization(organizationId);
        organization.updateMovementRoute(route);
        var session=sessions.findActiveSessionForUpdate(organizationId);
        if (session!=null) session.getMovement().loseSignal();
    }
    public void review(Authentication auth, Long sessionId, String note) {
        Long organizationId=current.requireOrganization(auth).getId();
        var actor=manager(auth,organizationId);
        sessions.lockOrganization(organizationId);
        var session=repository.findForReview(sessionId,organizationId)
                .orElseThrow(() -> new IllegalStateException("The session was not found in this organization."));
        session.reviewInactivity(actor,note,Instant.now());
    }
    static Snapshot snapshot(DrivingSession session, Instant now) {
        var m=session.getMovement();
        String state=!session.isActive() ? "ENDED" : !m.enabled() ? "DISABLED"
                : !m.fresh(now) ? "UNCONFIRMED" : m.getPromptedAt()!=null ? "PROMPT"
                : m.waiting() ? "WAITING" : "MONITORING";
        return new Snapshot(session.getId(),session.isActive(),m.enabled(),state,
                session.getOrganization().getMovementRoute()!=null && !session.getOrganization().getMovementRoute().isBlank(),
                session.getLastConfirmedAt(),m.getReceivedAt(),m.getMovingAt(),m.getStationarySince(),m.getPromptedAt(),m.timeoutAt(),
                session.getEndReason()==null ? null : session.getEndReason().name());
    }
    public record Snapshot(Long sessionId, boolean active, boolean enabled, String state, boolean routeConfigured,
            Instant lastConfirmedAt, Instant locationReceivedAt, Instant movementConfirmedAt, Instant stationarySince,
            Instant promptedAt, Instant timeoutAt, String endReason) {}
}
