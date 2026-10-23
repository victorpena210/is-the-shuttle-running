package technology.renatus.routepulse.driving;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService;
import technology.renatus.routepulse.driver.service.portal.DriverCheckpointService.CheckpointAction;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.driving.service.DrivingSessionService;
import technology.renatus.routepulse.driving.service.PassengerRideService;
import technology.renatus.routepulse.driving.service.RiderStatusService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.reporting.workload.service.DrivingRecordService;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class UnscheduledDrivingIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DriverCheckpointService checkpoints;
    @Autowired DrivingSessionService sessions;
    @Autowired DrivingSessionRepository sessionRepository;
    @Autowired DrivingRecordService records;
    @Autowired PassengerRideService rides;
    @Autowired RiderStatusService riderStatus;

    private Fixture fixture(String suffix) {
        Organization org = createOrganization("Unscheduled " + suffix, "unscheduled-" + suffix);
        UserAccount user = createUser("unscheduled-" + suffix + "@example.com", true);
        membershipRepository.save(new OrganizationMembership(org, user, OrganizationRole.DRIVER));
        Driver driver = new Driver(org, "100", "Driver " + suffix, true);
        driver.linkUser(user);
        driverRepository.saveAndFlush(driver);
        return new Fixture(org, user, driver);
    }

    @Test
    void noAssignmentFlowPersistsAndRendersStartPauseAndHistory() throws Exception {
        Fixture f = fixture("flow");
        long initialCount = sessionRepository.count();
        var auth = user(f.user().getEmail()).roles("DRIVER");
        mockMvc.perform(get("/driver/tap").with(auth).param("code", f.org().getDrivingQrCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirm start driving")));
        assertThat(sessionRepository.count()).isEqualTo(initialCount); // GET is read-only
        mockMvc.perform(post("/driver/tap").with(auth).param("code", f.org().getDrivingQrCode())
                .param("action", "START_UNSCHEDULED")).andExpect(status().isForbidden());
        mockMvc.perform(post("/driver/tap").with(auth).with(csrf()).param("code", f.org().getDrivingQrCode())
                .param("action", "START_UNSCHEDULED")).andExpect(status().is3xxRedirection());
        entityManager.flush();
        DrivingSession active = sessions.findActiveSession(f.org().getId());
        assertThat(active).isNotNull();
        assertThat(active.getAssignment()).isNull();
        assertThat(active.getDriver().getId()).isEqualTo(f.driver().getId());
        assertThat(active.getStartedBy().getId()).isEqualTo(f.user().getId());
        assertThat(active.getStartMethod()).isEqualTo(DrivingActionMethod.NFC);
        assertThat(assignmentRepository.findAllByOrganization_IdAndAssignmentDate(f.org().getId(), LocalDate.now(ZoneOffset.UTC))).isEmpty();
        mockMvc.perform(get("/driver").with(auth)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your shuttle session is active")));
        assertThat(riderStatus.getStatus(f.org().getSlug()).confirmationAvailable()).isTrue();
        assertThat(rides.confirmRide(f.org().getSlug(), "test-device")).isEqualTo(PassengerRideService.ConfirmationResult.RECORDED);
        assertThat(rides.confirmRide(f.org().getSlug(), "test-device")).isEqualTo(PassengerRideService.ConfirmationResult.ALREADY_CONFIRMED);
        checkpoints.recordCheckpoint(authenticationFor(f.user(), OrganizationRole.DRIVER), f.org().getDrivingQrCode(),
                DrivingActionMethod.NFC, CheckpointAction.PAUSE);
        assertThat(active.getEndReason()).isEqualTo(DrivingSessionEndReason.PAUSE);
        assertThat(sessions.findActiveSession(f.org().getId())).isNull();
        checkpoints.recordCheckpoint(authenticationFor(f.user(), OrganizationRole.DRIVER), f.org().getDrivingQrCode(),
                DrivingActionMethod.QR, CheckpointAction.START_UNSCHEDULED);
        DrivingSession second = sessions.findActiveSession(f.org().getId());
        assertThat(second.getId()).isNotEqualTo(active.getId());
        checkpoints.recordCheckpoint(authenticationFor(f.user(), OrganizationRole.DRIVER), f.org().getDrivingQrCode(),
                DrivingActionMethod.QR, CheckpointAction.END);
        assertThat(second.getEndReason()).isEqualTo(DrivingSessionEndReason.COMPLETED);
    }

    @Test
    void reportingIncludesActualMinutesWithoutScheduledWorkAndIsOrganizationScoped() throws Exception {
        Fixture f = fixture("reports");
        Fixture other = fixture("other");
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        DrivingSession session = sessions.startUnscheduledSession(f.driver(), f.user(), start, DrivingActionMethod.NFC);
        sessions.endSession(null, session, f.user(), start.plusSeconds(2700), DrivingActionMethod.NFC);
        entityManager.flush();
        LocalDate date = start.atZone(ZoneOffset.UTC).toLocalDate();
        var summary = records.getWorkloadSummary(f.org().getId(), date, date, f.driver().getId());
        assertThat(summary.getActualMinutes()).isEqualTo(45);
        assertThat(summary.getScheduledMinutes()).isZero();
        assertThat(summary.getScheduledShifts()).isZero();
        assertThat(records.getUnscheduledRecords(f.org().getId(), date, date, f.driver().getId())).hasSize(1);
        assertThat(records.getUnscheduledRecords(other.org().getId(), date, date, null)).isEmpty();
        assertThatThrownBy(() -> records.getUnscheduledRecords(other.org().getId(), date, date, f.driver().getId()))
                .isInstanceOf(IllegalArgumentException.class);
        UserAccount admin = createUser("unscheduled-admin@example.com", true);
        membershipRepository.save(new OrganizationMembership(f.org(), admin, OrganizationRole.ADMIN));
        mockMvc.perform(get("/driving-records").with(user(admin.getEmail()).roles("ADMIN"))
                .param("view", "records")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Driving history")));
    }

    @Test
    void handoffPreservesBothUnscheduledSessionsAndRejectsOtherOrganizations() {
        Fixture f = fixture("handoff");
        Fixture other = fixture("foreign");
        Driver replacement = driverRepository.save(new Driver(f.org(), "200", "Replacement Driver", true));
        Instant start = Instant.now().minusSeconds(3600);
        DrivingSession first = sessions.startUnscheduledSession(f.driver(), f.user(), start, DrivingActionMethod.NFC);
        assertThatThrownBy(() -> sessions.startUnscheduledSession(replacement, f.user(), start, DrivingActionMethod.NFC))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> sessions.takeOverActiveSession(first, other.driver(), f.user(), start.plusSeconds(1800), DrivingActionMethod.NFC))
                .isInstanceOf(IllegalArgumentException.class);
        DrivingSession second = sessions.takeOverActiveSession(first, replacement, f.user(), start.plusSeconds(1800), DrivingActionMethod.QR);
        assertThat(first.getEndReason()).isEqualTo(DrivingSessionEndReason.HANDOFF);
        assertThat(first.getActualMinutes()).isEqualTo(30);
        assertThat(second.isUnscheduled()).isTrue();
        assertThat(second.getStartedAt()).isEqualTo(first.getEndedAt());
        assertThat(second.isActive()).isTrue();
    }

    @Test
    void invalidTagDoesNotAllowAnUnscheduledStart() {
        Fixture f = fixture("invalid");
        assertThatThrownBy(() -> checkpoints.recordCheckpoint(authenticationFor(f.user(), OrganizationRole.DRIVER),
                "another-organizations-tag", DrivingActionMethod.NFC, CheckpointAction.START_UNSCHEDULED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sessions.findActiveSession(f.org().getId())).isNull();
    }

    @Test
    void inactiveDriverCannotStartWithoutAnAssignment() {
        Fixture f = fixture("inactive");
        f.driver().setActive(false);
        assertThatThrownBy(() -> checkpoints.recordCheckpoint(authenticationFor(f.user(), OrganizationRole.DRIVER),
                f.org().getDrivingQrCode(), DrivingActionMethod.NFC, CheckpointAction.START_UNSCHEDULED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inactive");
        assertThat(sessions.findActiveSession(f.org().getId())).isNull();
    }

    private record Fixture(Organization org, UserAccount user, Driver driver) {}
}
