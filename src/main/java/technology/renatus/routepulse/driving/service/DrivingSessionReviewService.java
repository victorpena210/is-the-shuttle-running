package technology.renatus.routepulse.driving.service;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.driving.repository.*;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.service.*;

@Service
@Transactional(readOnly = true)
public class DrivingSessionReviewService {
    private final DrivingSessionRepository sessions;
    private final DrivingSessionTimeReviewRepository reviews;
    private final DrivingSessionService lifecycle;
    private final CurrentOrganizationService current;
    private final OrganizationActorService actors;
    private final OrganizationMembershipRepository memberships;
    private final ZoneId zone;

    public DrivingSessionReviewService(DrivingSessionRepository sessions, DrivingSessionTimeReviewRepository reviews,
            DrivingSessionService lifecycle, CurrentOrganizationService current, OrganizationActorService actors,
            OrganizationMembershipRepository memberships, @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.sessions = sessions; this.reviews = reviews; this.lifecycle = lifecycle; this.current = current;
        this.actors = actors; this.memberships = memberships; this.zone = ZoneId.of(zone);
    }

    private Manager manager(Authentication auth) {
        Long orgId = current.requireOrganizationId(auth);
        var actor = actors.loadActor(orgId, auth.getName());
        var membership = memberships.findByOrganization_IdAndUser_Id(orgId, actor.getId()).orElseThrow(
                () -> new AccessDeniedException("Organization access is required."));
        if (!actor.isEnabled() || !Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.DISPATCHER)
                .contains(membership.getRole())) throw new AccessDeniedException("Manager access is required.");
        return new Manager(orgId, actor);
    }

    public long pendingCount(Authentication auth) {
        return sessions.countByOrganization_IdAndDurationReviewRequiredTrueAndTimeReviewedAtIsNull(manager(auth).organizationId());
    }

    public List<UnscheduledDrivingRecord> pending(Authentication auth) {
        return sessions.findTop50ByOrganization_IdAndDurationReviewRequiredTrueAndTimeReviewedAtIsNullOrderByStartedAtAsc(
                manager(auth).organizationId()).stream().map(s -> UnscheduledDrivingRecord.from(s, zone)).toList();
    }

    public ReviewPage page(Authentication auth, Long id) {
        var manager = manager(auth);
        var session = sessions.findByIdAndOrganization_Id(id, manager.organizationId()).orElseThrow(
                () -> new IllegalArgumentException("The session was not found in this organization."));
        return new ReviewPage(UnscheduledDrivingRecord.from(session, zone), session.isDurationReviewNeeded(),
                ReviewForm.forSession(session, zone), reviews
                .findBySession_IdAndSession_Organization_IdOrderByIdDesc(id, manager.organizationId()).stream()
                .map(r -> new AuditRow(r.getAction(), r.getReviewer().getFirstName() + " " + r.getReviewer().getLastName(),
                        r.getReviewedAt().atZone(zone), r.getPreviousStart().atZone(zone),
                        r.getPreviousEnd() == null ? null : r.getPreviousEnd().atZone(zone),
                        r.getCorrectedStart().atZone(zone), r.getCorrectedEnd().atZone(zone), r.getNote())).toList(), zone.getId());
    }

    @Transactional
    public void review(Authentication auth, Long id, ReviewForm form) {
        var manager = manager(auth);
        lifecycle.lockOrganization(manager.organizationId());
        var session = sessions.findForReview(id, manager.organizationId()).orElseThrow(
                () -> new IllegalArgumentException("The session was not found in this organization."));
        if (form.revision() != session.getTimeReviewRevision()
                || !Objects.equals(form.expectedStart(), session.getStartedAt())
                || !Objects.equals(form.expectedEnd(), session.getEndedAt()))
            throw new IllegalStateException("This session changed since you opened it. Reload the page before reviewing.");
        Instant now = Instant.now();
        Instant start, end;
        String note = form.note() == null ? "" : form.note().trim();
        if (note.length() > 1000) throw new IllegalArgumentException("Keep the review note under 1,001 characters.");
        if ("APPROVED".equals(form.action())) {
            if (!session.isDurationReviewNeeded() || session.isActive())
                throw new IllegalStateException("This session does not need approval. You can still correct its times.");
            start = session.getStartedAt(); end = session.getEndedAt();
            if (note.isEmpty()) note = "Approved the recorded times.";
        } else if ("CORRECTED".equals(form.action())) {
            if (note.isEmpty()) throw new IllegalArgumentException("Add a reason for the time correction.");
            start = resolveTime(form.startedAt(), form.startOccurrence(), zone);
            end = resolveTime(form.endedAt(), form.endOccurrence(), zone);
            if (!end.isAfter(start) || end.isAfter(now))
                throw new IllegalArgumentException("The end must be after the start and cannot be in the future.");
            if (sessions.countOverlappingDriverSessions(manager.organizationId(), session.getDriver().getId(), id, start, end) > 0)
                throw new IllegalArgumentException("These times overlap another session for this driver. Correct the overlap first.");
        } else throw new IllegalArgumentException("Choose approval or a time correction.");
        reviews.save(new DrivingSessionTimeReview(session, manager.actor(), now, form.action(), start, end, note));
        session.reviewTimes(start, end, manager.actor(), now);
    }

    static Instant resolveTime(String value, String occurrence, ZoneId zone) {
        try {
            LocalDateTime local = LocalDateTime.parse(value == null ? "" : value);
            var offsets = zone.getRules().getValidOffsets(local);
            if (offsets.isEmpty()) throw new IllegalArgumentException("That local time does not exist because clocks moved forward. Choose another time.");
            if (!"earlier".equals(occurrence) && !"later".equals(occurrence))
                throw new IllegalArgumentException("Choose the first or second occurrence for repeated clock times.");
            return local.toInstant(offsets.get("later".equals(occurrence) ? offsets.size() - 1 : 0));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter both a valid start date/time and end date/time.");
        }
    }

    public record ReviewForm(long revision, Instant expectedStart, Instant expectedEnd, String action,
            String startedAt, String endedAt, String startOccurrence, String endOccurrence, String note) {
        static ReviewForm forSession(DrivingSession s, ZoneId zone) {
            var start = s.getStartedAt().atZone(zone);
            var end = (s.getEndedAt() == null ? Instant.now() : s.getEndedAt()).atZone(zone);
            return new ReviewForm(s.getTimeReviewRevision(), s.getStartedAt(), s.getEndedAt(), "CORRECTED",
                    start.toLocalDateTime().withNano(0).toString(), end.toLocalDateTime().withNano(0).toString(),
                    occurrence(start), occurrence(end), "");
        }
        private static String occurrence(ZonedDateTime date) {
            return date.withEarlierOffsetAtOverlap().getOffset().equals(date.getOffset()) ? "earlier" : "later";
        }
    }
    private record Manager(Long organizationId, UserAccount actor) {}
    public record ReviewPage(UnscheduledDrivingRecord record, boolean needsReview, ReviewForm form,
            List<AuditRow> audit, String zone) {}
    public record AuditRow(String action, String reviewer, ZonedDateTime reviewedAt, ZonedDateTime previousStart,
            ZonedDateTime previousEnd, ZonedDateTime correctedStart, ZonedDateTime correctedEnd, String note) {}
}
