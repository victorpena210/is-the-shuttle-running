package technology.renatus.routepulse.scheduling.demand;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;
import technology.renatus.routepulse.wardrobe.*;

class DriverSuggestionIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DemandForecastService forecasts;
    @Autowired DriverSuggestionService suggestions;
    @Autowired DriverScheduleWindowRepository schedules;
    @Autowired DrivingSessionRepository sessions;
    @Autowired DriverScheduleIdentityService identities;
    @Autowired WardrobeHoursService wardrobe;
    private static final LocalDate DAY = LocalDate.now(ZoneOffset.UTC).plusDays(1);
    private static final Instant NOW = DAY.minusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC);

    private Fixture fixture(String slug) {
        var org = createOrganization(slug, slug); var manager = createUser(slug + "@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org, manager, OrganizationRole.OWNER));
        var alice = driverRepository.saveAndFlush(new Driver(org, "100", "Alice", true));
        var bob = driverRepository.saveAndFlush(new Driver(org, "200", "Bob", true));
        return new Fixture(org, manager, alice, bob);
    }
    private void upload(Fixture f, String... rows) { forecasts.upload(f.org().getId(), f.manager().getEmail(), csvFile("schedule.csv", rows)); }
    private String row(String id, LocalDate date, String start, String end) {
        LocalDate endDate = LocalTime.parse(end).isAfter(LocalTime.parse(start)) ? date : date.plusDays(1);
        var format = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US);
        return id + ",PRIVATE UPLOADED NAME," + date.atTime(LocalTime.parse(start)).format(format) + ","
                + endDate.atTime(LocalTime.parse(end)).format(format) + ",Park/Ops,Staff";
    }
    private DriverSuggestionService.Suggestions page(Fixture f) {
        return suggestions.page(f.org().getId(), forecasts.page(f.org().getId(), DAY), NOW);
    }
    private DriverSuggestionService.Window window(DriverSuggestionService.Suggestions page, String time) {
        LocalDateTime at = DAY.atTime(LocalTime.parse(time));
        return page.periods().stream().flatMap(p -> p.windows().stream())
                .filter(w -> !at.isBefore(w.start()) && at.isBefore(w.end())).findFirst().orElseThrow();
    }
    private DrivingSession drove(Fixture f, Driver driver, Instant start, int minutes) {
        var session = DrivingSession.startUnscheduled(f.org(), driver, start, f.manager(), DrivingActionMethod.MANAGER);
        session.end(start.plusSeconds(minutes * 60L), f.manager(), DrivingActionMethod.MANAGER);
        return sessions.saveAndFlush(session);
    }

    @Test void ranksActualHoursThenLongestSinceDrivingAndReflectsManagerCorrections() {
        var f = fixture("rank");
        upload(f, row("100", DAY, "08:00", "20:00"), row("200", DAY, "08:00", "20:00"), row("other", DAY, "09:00", "19:00"));
        var start = NOW.minus(Duration.ofDays(2));
        drove(f, f.alice(), start, 120); var bobSession = drove(f, f.bob(), start.plusSeconds(5*3600), 30);
        assertThat(window(page(f), "09:00").recommended().name()).isEqualTo("Bob");
        assertThat(window(page(f), "09:00").recommended().drivingTime()).isEqualTo("30 min");
        bobSession.reviewTimes(bobSession.getStartedAt(), bobSession.getStartedAt().plusSeconds(3*3600), f.manager(), NOW);
        sessions.flush();
        assertThat(window(page(f), "09:00").recommended().name()).isEqualTo("Alice");
        bobSession.reviewTimes(bobSession.getStartedAt(), bobSession.getStartedAt().plusSeconds(2*3600), f.manager(), NOW);
        sessions.flush();
        assertThat(window(page(f), "09:00").recommended().name()).isEqualTo("Alice");
        assertThat(assignmentRepository.count()).isZero(); assertThat(sessions.count()).isEqualTo(2);
        assertThat(uploadedShiftRepository.count()).isZero();
    }

    @Test void requiresFullWindowRespectsSplitShiftGapsAndExcludesInactiveDrivers() {
        var f = fixture("coverage"); f.alice().setActive(false); driverRepository.flush();
        upload(f, row("100", DAY, "08:00", "23:00"), row("200", DAY, "19:07", "20:00"),
                row("200", DAY, "20:15", "22:00"), row("other", DAY, "19:00", "20:00"),
                row("next", DAY, "19:15", "20:15"));
        assertThat(window(page(f), "19:00").recommended()).isNull();
        assertThat(window(page(f), "19:15").recommended().name()).isEqualTo("Bob");
        assertThat(window(page(f), "20:00").recommended()).isNull();
        assertThat(window(page(f), "20:15").recommended().name()).isEqualTo("Bob");
        assertThat(page(f).roster()).extracting(DriverSuggestionService.Candidate::name).containsExactly("Bob");
    }

    @Test void onlyDistinctScheduledRosterMembersSupplyCounterCoverageAndMiddayWaitsForClosing() {
        var f = fixture("counter");
        upload(f, row("100", DAY, "08:00", "20:00"), row("100", DAY, "08:00", "20:00"),
                row("not-a-driver", DAY, "09:00", "14:00"));
        assertThat(window(page(f), "09:00").recommended()).isNull();
        assertThat(window(page(f), "09:00").reason()).contains("wardrobe counter");
        upload(f, row("100", DAY, "08:00", "20:00"), row("200", DAY, "08:00", "20:00"),
                row("other", DAY, "09:00", "14:00"));
        assertThat(window(page(f), "09:00").recommended()).isNotNull();
        assertThat(window(page(f), "14:00").reason()).contains("wardrobe closes");
        wardrobe.save(f.org().getId(), WardrobeHoursRule.Scope.DAY, "Early close", DAY, DAY,
                LocalTime.of(9,0), LocalTime.of(13,0), null, f.manager().getEmail());
        assertThat(window(page(f), "14:00").recommended()).isNotNull();
        assertThat(assignmentRepository.count()).isZero();
    }

    @Test void afternoonOpeningUsesCounterCoverageAndOvernightAvailabilityEndsOnTheCorrectDate() {
        var f = fixture("overnight");
        upload(f, row("100", DAY, "15:00", "01:00"), row("200", DAY, "15:00", "23:00"),
                row("other", DAY, "15:00", "23:45"));
        assertThat(window(page(f), "15:00").candidates()).hasSize(2);
        assertThat(window(page(f), "23:45").recommended().name()).isEqualTo("Alice");
        var overnight = schedules.overlapping(f.org().getId(), DAY.plusDays(1).atStartOfDay(), DAY.plusDays(1).atTime(2,0));
        assertThat(overnight).hasSize(1);
        assertThat(DriverSuggestionService.covers(overnight, DAY.plusDays(1).atStartOfDay(), DAY.plusDays(1).atTime(1,0))).isTrue();
        assertThat(DriverSuggestionService.covers(overnight, DAY.plusDays(1).atTime(1,0), DAY.plusDays(1).atTime(1,15))).isFalse();
    }

    @Test void reuploadReplacesIncludedDatesClearsMissingDriversAndKeepsOtherDates() {
        var f = fixture("replace-drivers");
        upload(f, row("100", DAY, "19:00", "01:00"), row("200", DAY.plusDays(1), "09:00", "20:00"));
        for (int i = 0; i < 2; i++) upload(f, row("other", DAY, "19:00", "20:00"));
        entityManager.flush(); entityManager.clear();
        assertThat(schedules.overlapping(f.org().getId(), DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())).isEmpty();
        assertThat(schedules.overlapping(f.org().getId(), DAY.plusDays(1).atStartOfDay(), DAY.plusDays(2).atStartOfDay())).hasSize(1);
        assertThat(page(f).unmatched()).hasSize(2);
        assertThat(forecasts.page(f.org().getId(), DAY).clockIns()).isOne();
    }

    @Test void malformedUploadDoesNotReplaceAvailabilityAndUnregisteredEmployeesAreNotStored() throws Exception {
        var f = fixture("invalid-driver-upload"); upload(f, row("100", DAY, "09:00", "20:00"), row("other", DAY, "09:00", "20:00"));
        assertThat(schedules.count()).isOne();
        var csv = csvFile("bad.csv", row("200", DAY, "08:00", "21:00"), "300,Broken,invalid,,Park/Ops,Staff");
        var file = new org.springframework.mock.web.MockMultipartFile("scheduleFile", "bad.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/transportation-demand/upload").file(file)
                .with(authentication(authenticationFor(f.manager(), OrganizationRole.OWNER))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertThat(schedules.count()).isOne();
        assertThat(schedules.findAll().get(0).getDriver().getId()).isEqualTo(f.alice().getId());
    }

    @Test void employeeIdChangesAreManagerOnlyScopedAndRequireFreshAvailability() throws Exception {
        var f = fixture("identity"); var foreign = fixture("foreign-identity");
        upload(f, row("100", DAY, "09:00", "20:00"));
        assertThatThrownBy(() -> identities.update(f.org().getId(), f.alice().getId(), "200", f.manager().getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> identities.update(f.org().getId(), foreign.alice().getId(), "300", f.manager().getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        mockMvc.perform(post("/drivers/{id}/employee-id", f.alice().getId()).param("employeeId","300").with(authentication(auth)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/drivers/{id}/employee-id", f.alice().getId()).param("employeeId","300").with(authentication(auth)).with(csrf()))
                .andExpect(redirectedUrl("/drivers")).andExpect(flash().attributeExists("successMessage"));
        assertThat(schedules.count()).isZero(); assertThat(f.alice().getEmployeeId()).isEqualTo("300");
        upload(f, row("300", DAY, "09:00", "20:00")); assertThat(schedules.count()).isOne();
        var user = createUser("identity-driver@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(f.org(), user, OrganizationRole.DRIVER));
        assertThatThrownBy(() -> identities.update(f.org().getId(), f.bob().getId(), "400", user.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void managerDemandLinksToAssignmentsWithoutExposingDriverDetails() throws Exception {
        var f = fixture("view-suggestions"); var foreign = fixture("private-suggestions");
        foreign.alice().changeEmployeeId("PRIVATE-ID"); driverRepository.flush();
        upload(f, row("100", DAY, "08:00", "20:00"), row("200", DAY, "08:00", "20:00"), row("other", DAY, "09:00", "19:00"));
        var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        mockMvc.perform(get("/transportation-demand").param("date", DAY.toString()).param("organizationId",foreign.org().getId().toString()).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Driving assignments")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/driving-schedule")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-ID"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE UPLOADED NAME"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/driving-schedule")));
        mockMvc.perform(get("/drivers").with(authentication(auth))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit employee ID")));
        var driverUser = createUser("suggestions-driver@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(f.org(), driverUser, OrganizationRole.DRIVER));
        f.bob().linkUser(driverUser); driverRepository.flush(); var driverAuth = authenticationFor(driverUser, OrganizationRole.DRIVER);
        mockMvc.perform(get("/transportation-demand").param("date", DAY.toString())
                .with(authentication(authenticationFor(driverUser, OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/driver/demand").param("date", DAY.toString()).with(authentication(driverAuth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Suggested drivers"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Alice"))));
        mockMvc.perform(post("/drivers/{id}/employee-id", f.alice().getId()).param("employeeId","300").with(authentication(driverAuth)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/transportation-demand/wardrobe-hours").param("date",DAY.toString()).param("openingTime","09:00")
                .param("closingTime","18:00").with(authentication(driverAuth)).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/transportation-demand/wardrobe-hours").param("date",DAY.toString()).param("openingTime","09:00")
                .param("closingTime","18:00").with(authentication(auth))).andExpect(status().isForbidden());
        mockMvc.perform(post("/transportation-demand/wardrobe-hours").param("date",DAY.toString()).param("openingTime","09:00")
                .param("closingTime","18:00").with(authentication(auth)).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
        assertThat(wardrobe.getSavedHours(f.org().getId(),DAY).closingTime()).isEqualTo(LocalTime.of(18,0));
        assertThat(assignmentRepository.count()).isZero();
    }

    @Test void workloadUnionsDuplicatesClipsThirtyDayBoundaryCapsOpenSessionsAndKeepsApprovedLongSessions() {
        var f = fixture("workload"); var from = NOW.minus(Duration.ofDays(30));
        var a = drove(f, f.alice(), from.minusSeconds(3600), 120);
        var duplicate = drove(f, f.alice(), from, 60);
        var active = DrivingSession.startUnscheduled(f.org(), f.alice(), NOW.minusSeconds(6*3600), f.manager(), DrivingActionMethod.MANAGER);
        var approved = drove(f, f.alice(), NOW.minusSeconds(24*3600), 180);
        approved.reviewTimes(approved.getStartedAt(), approved.getStartedAt().plusSeconds(5*3600), f.manager(), NOW);
        assertThat(DriverSuggestionService.seconds(List.of(a,duplicate,active,approved), from,NOW)).isEqualTo(10*3600);
    }

    @Test void sameDayDrivingRotatesBehindAvailableDriversWhoHaveNotDrivenYet() {
        var f = fixture("today-rotation");
        LocalDate today = NOW.atZone(ZoneOffset.UTC).toLocalDate();
        upload(f, row("100", today, "08:00", "20:00"), row("200", today, "08:00", "20:00"), row("other", today, "09:00", "19:00"));
        drove(f, f.alice(), NOW.minusSeconds(3600), 10); drove(f, f.bob(), NOW.minus(Duration.ofDays(1)), 120);
        var result = suggestions.page(f.org().getId(), forecasts.page(f.org().getId(), today), NOW);
        assertThat(result.periods().get(0).windows().get(0).recommended().name()).isEqualTo("Bob");
    }

    @Test void currentDriverCannotCoverTheCounterOrBeSuggestedPastTheFourHourCutoff() {
        var f = fixture("active-cutoff"); var today = NOW.atZone(ZoneOffset.UTC).toLocalDate();
        upload(f, row("100",today,"05:00","20:00"),row("200",today,"08:00","20:00"),
                row("other",today,"09:00","19:00"),row("next",today,"09:15","19:15"));
        sessions.saveAndFlush(DrivingSession.startUnscheduled(f.org(),f.alice(),today.atTime(5,15).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER));
        var result = suggestions.page(f.org().getId(),forecasts.page(f.org().getId(),today),today.atTime(9,7).toInstant(ZoneOffset.UTC));
        var morning = result.periods().get(0).windows();
        assertThat(morning.get(0).start().toLocalTime()).isEqualTo(LocalTime.of(9,0));
        assertThat(morning.get(0).candidates()).extracting(DriverSuggestionService.Candidate::name).containsExactly("Alice");
        assertThat(morning.get(1).start().toLocalTime()).isEqualTo(LocalTime.of(9,15));
        assertThat(morning.get(1).candidates()).extracting(DriverSuggestionService.Candidate::name).containsExactly("Bob");
        // The same cutoff applies to continuing morning service, not the separate evening run.
        assertThat(result.periods().stream().flatMap(p->p.windows().stream())
                .filter(w->w.start().equals(today.atTime(19,0))).findFirst().orElseThrow().candidates())
                .extracting(DriverSuggestionService.Candidate::name).containsExactlyInAnyOrder("Alice","Bob");
    }

    @Test void continuingServiceStillHonorsTheCurrentRunCutoffAcrossMidnight() {
        var f=fixture("overnight-active-cutoff");var today=NOW.atZone(ZoneOffset.UTC).toLocalDate();
        upload(f,row("100",today,"19:00","03:00"),row("200",today,"19:00","03:00"));
        var run=DrivingSession.startUnscheduled(f.org(),f.alice(),today.atTime(21,15).toInstant(ZoneOffset.UTC),f.manager(),DrivingActionMethod.MANAGER);
        sessions.saveAndFlush(run);
        var park=new technology.renatus.routepulse.calendar.domain.ParkOperatingDay(today,
                technology.renatus.routepulse.calendar.domain.ParkOperatingStatus.OPEN,
                LocalTime.of(17,0),LocalTime.of(2,0),false,"LIVE_API",null);
        var result=suggestions.forAssignments(f.org().getId(),forecasts.page(f.org().getId(),today),
                today.atTime(23,7).toInstant(ZoneOffset.UTC),wardrobe.getSavedHours(f.org().getId(),today),false,park);
        var cutoff=today.plusDays(1).atTime(1,15);
        assertThat(result.periods().stream().flatMap(p->p.windows().stream()).filter(w->w.end().equals(cutoff)).findFirst().orElseThrow().candidates())
                .extracting(DriverSuggestionService.Candidate::name).contains("Alice");
        assertThat(result.periods().stream().flatMap(p->p.windows().stream()).filter(w->w.start().equals(cutoff)).findFirst().orElseThrow().candidates())
                .extracting(DriverSuggestionService.Candidate::name).containsExactly("Bob");
        assertThat(run.isActive()).isTrue();assertThat(run.getEndedAt()).isNull();
    }

    @Test void retentionRemovesOldDriverHoursAndKeepsFutureSchedulesAndAnonymousCounts() {
        var f = fixture("driver-retention"); var old = LocalDate.now(ZoneOffset.UTC).minusDays(40);
        upload(f,row("100",old,"09:00","20:00"),row("100",DAY,"09:00","20:00"));
        forecasts.purgeExpiredForecasts();
        assertThat(schedules.count()).isOne();
        assertThat(schedules.findAll().get(0).getStartsAt().toLocalDate()).isEqualTo(DAY);
        assertThat(forecasts.page(f.org().getId(),old).available()).isTrue();
    }
    private record Fixture(Organization org, UserAccount manager, Driver alice, Driver bob) {}
}
