package technology.renatus.routepulse.driving;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.*;
import technology.renatus.routepulse.driving.model.*;
import technology.renatus.routepulse.driving.repository.*;
import technology.renatus.routepulse.driving.service.*;
import technology.renatus.routepulse.driving.service.DrivingSessionReviewService.ReviewForm;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.reporting.workload.service.ActualDrivingRecordService;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingSessionReviewIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DrivingSessionRepository sessions;
    @Autowired DrivingSessionTimeReviewRepository audits;
    @Autowired DrivingSessionReviewService reviews;
    @Autowired DrivingSessionService lifecycle;
    @Autowired ActualDrivingRecordService records;
    private static final Instant START = Instant.parse("2026-01-12T09:00:00Z");

    private Fixture fixture(String slug) {
        var org = createOrganization(slug, slug);
        var manager = createUser(slug + "@example.com", true);
        membershipRepository.save(new OrganizationMembership(org, manager, OrganizationRole.OWNER));
        var driver = driverRepository.save(new Driver(org, "100", "Test Driver", true));
        var run = sessions.saveAndFlush(DrivingSession.startUnscheduled(org, driver, START, manager, DrivingActionMethod.NFC));
        lifecycle.endOverdueSessions(org.getId(), START.plusSeconds(5 * 3600));
        return new Fixture(org.getId(), manager, run);
    }

    @Test void automaticStopAlertsAcrossAllDatesAndApprovalPreservesTimesAndAudit() throws Exception {
        var f = fixture("approve");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        assertThat(reviews.pendingCount(auth)).isOne();
        assertThat(reviews.pending(auth)).extracting(UnscheduledDrivingRecord::id).containsExactly(f.run().getId());
        mockMvc.perform(get("/driving-records/reviews").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Test Driver")));
        mockMvc.perform(get("/driving-records/reviews/count").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        var page = reviews.page(auth, f.run().getId());
        mockMvc.perform(post("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(auth)).with(csrf())
                .param("revision", "0").param("expectedStart", page.form().expectedStart().toString())
                .param("expectedEnd", page.form().expectedEnd().toString()).param("action", "APPROVED"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush(); entityManager.clear();
        var saved = sessions.findById(f.run().getId()).orElseThrow();
        assertThat(saved.getStartedAt()).isEqualTo(START);
        assertThat(saved.getActualMinutes()).isEqualTo(240);
        assertThat(saved.getEndReason()).isEqualTo(DrivingSessionEndReason.DURATION_LIMIT);
        assertThat(reviews.pendingCount(auth)).isZero();
        var history = reviews.page(auth, saved.getId()).audit();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).action()).isEqualTo("APPROVED");
        assertThat(history.get(0).previousEnd().toInstant()).isEqualTo(START.plusSeconds(4 * 3600));
        assertThat(history.get(0).reviewer()).isEqualTo("Test User");
        mockMvc.perform(get("/driving-records/sessions/{id}/review", saved.getId()).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Times approved")));
    }

    @Test void correctionsChangeBothTimesAndTotalsRetainOriginalAndAllowVerifiedLongRuns() {
        var f = fixture("correct");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        var original = reviews.page(auth, f.run().getId()).form();
        reviews.review(auth, f.run().getId(), corrected(original, "2026-01-11T23:00", "2026-01-12T04:30", "Verified actual driving."));
        entityManager.flush(); entityManager.clear();
        var saved = sessions.findById(f.run().getId()).orElseThrow();
        assertThat(saved.getActualMinutes()).isEqualTo(330);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.isDurationReviewNeeded()).isFalse();
        assertThat(records.build(f.orgId(), LocalDate.of(2026,1,11), LocalDate.of(2026,1,11), null).minutes()).isEqualTo(60);
        assertThat(records.build(f.orgId(), LocalDate.of(2026,1,12), LocalDate.of(2026,1,12), null).minutes()).isEqualTo(270);
        var audit = audits.findBySession_IdAndSession_Organization_IdOrderByIdDesc(saved.getId(), f.orgId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getPreviousStart()).isEqualTo(START);
        assertThat(audit.get(0).getPreviousEnd()).isEqualTo(START.plusSeconds(14400));
        assertThat(audit.get(0).getReviewer().getId()).isEqualTo(f.manager().getId());
        assertThatThrownBy(() -> reviews.review(auth, saved.getId(), corrected(original, "2026-01-12T10:00", "2026-01-12T11:00", "Stale tab")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed since");
    }

    @Test void managerCanCloseForgottenActiveRunAndOriginalOpenRecordRemainsInAudit() {
        var f = fixture("active");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        Instant started = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var active = sessions.saveAndFlush(DrivingSession.startUnscheduled(f.run().getOrganization(), f.run().getDriver(), started,
                f.manager(), DrivingActionMethod.QR));
        var form = reviews.page(auth, active.getId()).form();
        reviews.review(auth, active.getId(), corrected(form, LocalDateTime.ofInstant(started, ZoneOffset.UTC).toString(),
                LocalDateTime.ofInstant(started.plusSeconds(1800), ZoneOffset.UTC).toString(), "Forgot to stop at the end."));
        entityManager.flush(); entityManager.clear();
        var saved = sessions.findById(active.getId()).orElseThrow();
        assertThat(saved.getActualMinutes()).isEqualTo(30);
        assertThat(saved.getEndMethod()).isEqualTo(DrivingActionMethod.MANAGER);
        assertThat(lifecycle.findActiveSession(f.orgId())).isNull();
        assertThat(reviews.page(auth, active.getId()).audit().get(0).previousEnd()).isNull();
    }

    @Test void invalidFutureOverlappingAndUnexplainedChangesDoNotWriteAudit() {
        var f = fixture("invalid");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        var form = reviews.page(auth, f.run().getId()).form();
        var other = DrivingSession.startUnscheduled(f.run().getOrganization(), f.run().getDriver(), START.plusSeconds(18000), f.manager(), DrivingActionMethod.QR);
        other.end(START.plusSeconds(21600), f.manager(), DrivingActionMethod.QR); sessions.saveAndFlush(other);
        for (var invalid : java.util.List.of(
                corrected(form, "2026-01-12T11:00", "2026-01-12T10:00", "Reverse"),
                corrected(form, "2026-01-12T09:00", "2099-01-12T10:00", "Future"),
                corrected(form, "2026-01-12T09:00", "2026-01-12T14:30", "Overlap"),
                corrected(form, "2026-01-12T09:00", "2026-01-12T10:00", " ")))
            assertThatThrownBy(() -> reviews.review(auth, f.run().getId(), invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(reviews.page(auth, f.run().getId()).audit()).isEmpty();
        assertThat(reviews.pendingCount(auth)).isOne();
    }

    @Test void correctionFormRendersAndSubmitsWithCsrfAndKeepsInvalidInput() throws Exception {
        var f = fixture("form");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        var form = reviews.page(auth, f.run().getId()).form();
        mockMvc.perform(get("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Approve recorded times")));
        var request = post("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(auth)).with(csrf())
                .param("revision", "0").param("expectedStart", form.expectedStart().toString()).param("expectedEnd", form.expectedEnd().toString())
                .param("action", "CORRECTED").param("startedAt", "2026-01-12T09:00").param("endedAt", "2026-01-12T10:30")
                .param("startOccurrence", "earlier").param("endOccurrence", "earlier").param("note", "Driver forgot to stop.");
        mockMvc.perform(request).andExpect(status().is3xxRedirection());
        assertThat(f.run().getActualMinutes()).isEqualTo(90);
        var current = reviews.page(auth, f.run().getId()).form();
        mockMvc.perform(post("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(auth)).with(csrf())
                .param("revision", "1").param("expectedStart", current.expectedStart().toString()).param("expectedEnd", current.expectedEnd().toString())
                .param("action", "CORRECTED").param("startedAt", "2026-01-12T09:00").param("endedAt", "2026-01-12T08:30")
                .param("startOccurrence", "earlier").param("endOccurrence", "earlier").param("note", "Keep my explanation"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Keep my explanation")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("cannot be in the future")));
    }

    @Test void driverCrossOrganizationAndMissingCsrfCannotReviewOrReadPrivateRecords() throws Exception {
        var f = fixture("secure"); var other = fixture("foreign");
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        mockMvc.perform(get("/driving-records/sessions/{id}/review", other.run().getId()).with(authentication(auth))).andExpect(status().isNotFound());
        mockMvc.perform(post("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(auth))).andExpect(status().isForbidden());
        var driverUser = createUser("driver-review@example.com", true);
        membershipRepository.save(new OrganizationMembership(f.run().getOrganization(), driverUser, OrganizationRole.DRIVER));
        var driverAuth = authenticationFor(driverUser, OrganizationRole.DRIVER);
        mockMvc.perform(get("/driving-records/reviews/count").with(authentication(driverAuth))).andExpect(status().isForbidden());
        mockMvc.perform(post("/driving-records/sessions/{id}/review", f.run().getId()).with(authentication(driverAuth)).with(csrf())).andExpect(status().isForbidden());
        assertThatThrownBy(() -> reviews.review(authenticationFor(driverUser, OrganizationRole.OWNER), f.run().getId(), reviews.page(auth, f.run().getId()).form()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> reviews.review(auth, other.run().getId(), reviews.page(authenticationFor(other.manager(), OrganizationRole.OWNER), other.run().getId()).form()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(reviews.pendingCount(auth)).isOne();
    }

    private ReviewForm corrected(ReviewForm f, String start, String end, String note) {
        return new ReviewForm(f.revision(), f.expectedStart(), f.expectedEnd(), "CORRECTED", start, end, "earlier", "earlier", note);
    }
    private record Fixture(Long orgId, technology.renatus.routepulse.auth.model.UserAccount manager, DrivingSession run) {}
}
