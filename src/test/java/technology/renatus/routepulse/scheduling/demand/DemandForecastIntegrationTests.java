package technology.renatus.routepulse.scheduling.demand;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DemandForecastIntegrationTests extends MySqlIntegrationTestSupport {
    @Autowired DemandForecastService forecasts;
    @Autowired DemandForecastRepository windows;
    @Autowired DrivingSessionRepository sessions;
    private static final LocalDate DAY = LocalDate.of(2026,10,24);

    private Fixture fixture(String slug) {
        var org = createOrganization(slug, slug);
        var manager = createUser(slug + "@example.com", true);
        membershipRepository.saveAndFlush(new OrganizationMembership(org, manager, OrganizationRole.OWNER));
        return new Fixture(org, manager);
    }

    @Test void uploadShowsEveryQuarterHourBothDirectionsAndCorrectOvernightDatesWithoutAssignments() throws Exception {
        var f = fixture("counts"); var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        var file = demandFile("weekly.csv",
                "100,PRIVATE EMPLOYEE,10/24/2026 9:07 AM,10/24/2026 1:07 PM,Park/Ops,Staff",
                "100,PRIVATE EMPLOYEE,10/24/2026 9:07 AM,10/24/2026 1:07 PM,Park/Ops,Staff",
                "200,PRIVATE EMPLOYEE,10/24/2026 9:14 AM,10/24/2026 9:16 AM,Park/Ops,Staff",
                "300,PRIVATE EMPLOYEE,10/24/2026 12:00 PM,10/24/2026 5:00 PM,Park/Ops,Staff",
                "400,PRIVATE EMPLOYEE,10/24/2026 5:08 PM,10/24/2026 11:59 PM,Park/Ops,Staff",
                "500,PRIVATE EMPLOYEE,10/24/2026 11:45 PM,10/25/2026 12:00 AM,Park/Ops,Staff",
                "600,PRIVATE EMPLOYEE,10/23/2026 11:00 PM,10/24/2026 12:30 AM,Park/Ops,Staff");
        mockMvc.perform(multipart("/transportation-demand/upload").file(file).with(authentication(auth)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/transportation-demand?date=2026-10-23"))
                .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.containsString("6 unique shifts")));
        entityManager.flush(); entityManager.clear();
        var page = forecasts.page(f.org().getId(), DAY);
        assertThat(page.clockIns()).isEqualTo(5); assertThat(page.clockOuts()).isEqualTo(5);
        assertThat(window(page, "morning", "09:00").clockIns()).isEqualTo(2);
        assertThat(window(page, "morning", "09:15").clockOuts()).isEqualTo(1);
        assertThat(window(page, "morning", "10:00").movements()).isZero();
        assertThat(window(page, "morning", "12:00").clockIns()).isOne();
        assertThat(window(page, "evening", "17:00").clockIns()).isOne();
        assertThat(window(page, "evening", "17:00").clockOuts()).isOne();
        assertThat(window(page, "evening", "23:45").endsNextDay()).isTrue();
        assertThat(page.offHours().windows().stream().filter(w -> w.start().toLocalTime().equals(LocalTime.of(0,30)))
                .findFirst().orElseThrow().clockOuts()).isOne();
        var tomorrow = forecasts.page(f.org().getId(), DAY.plusDays(1));
        assertThat(tomorrow.clockIns()).isZero(); assertThat(tomorrow.clockOuts()).isOne();
        assertThat(page.peak().start().toLocalTime()).isEqualTo(LocalTime.of(9,0));
        assertThat(uploadedShiftRepository.count()).isZero();
        assertThat(assignmentRepository.count()).isZero(); assertThat(sessions.count()).isZero();
        mockMvc.perform(get("/transportation-demand").param("date", DAY.toString()).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Morning Arrivals")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Midday Turnover")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Evening Pickups")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Clocking out")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE EMPLOYEE"))));
    }

    @Test void repeatedPartialUploadsReplaceTheirWorkDatesAndOvernightCarryoverButKeepOtherDates() {
        var f = fixture("replace");
        forecasts.upload(f.org().getId(), f.manager().getEmail(), demandFile("first.csv",
                "100,One,10/24/2026 11:00 PM,10/25/2026 12:30 AM,Park/Ops,Staff",
                "200,Two,10/25/2026 9:00 AM,10/25/2026 5:00 PM,Park/Ops,Staff"));
        assertThat(forecasts.page(f.org().getId(), DAY.plusDays(1)).clockOuts()).isEqualTo(2);
        for (int i = 0; i < 2; i++) forecasts.upload(f.org().getId(), f.manager().getEmail(), demandFile("replace.csv",
                "100,One,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"));
        entityManager.flush(); entityManager.clear();
        assertThat(forecasts.page(f.org().getId(), DAY).clockIns()).isOne();
        assertThat(forecasts.page(f.org().getId(), DAY).clockOuts()).isOne();
        assertThat(forecasts.page(f.org().getId(), DAY.plusDays(1)).clockIns()).isOne();
        assertThat(forecasts.page(f.org().getId(), DAY.plusDays(1)).clockOuts()).isOne();
        assertThat(scheduleImportRepository.count()).isEqualTo(3);
    }

    @Test void malformedReplacementLeavesExistingCountsAndImportHistoryUntouched() throws Exception {
        var f = fixture("invalid"); var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        forecasts.upload(f.org().getId(), f.manager().getEmail(), demandFile("valid.csv",
                "100,One,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"));
        mockMvc.perform(multipart("/upload").file(demandFile("bad.csv",
                "200,Two,10/24/2026 10:00 AM,10/24/2026 6:00 PM,Park/Ops,Staff",
                "300,Three,10/24/2026 10:00 AM,,Park/Ops,Staff")).with(authentication(auth)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertThat(scheduleImportRepository.count()).isOne();
        assertThat(forecasts.page(f.org().getId(), DAY).clockIns()).isOne();
        assertThat(window(forecasts.page(f.org().getId(), DAY), "morning", "09:00").clockIns()).isOne();
    }

    @Test void managerAndDriverViewsAreOrganizationScopedAndDriversCannotUpload() throws Exception {
        var f = fixture("access"); var other = fixture("elsewhere");
        forecasts.upload(other.org().getId(), other.manager().getEmail(), demandFile("PRIVATE-FOREIGN.csv",
                "100,Foreign,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"));
        var driverUser = createUser("demand-driver@example.com", true);
        membershipRepository.save(new OrganizationMembership(f.org(), driverUser, OrganizationRole.DRIVER));
        var driver = new Driver(f.org(), "driver", "Driver", true); driver.linkUser(driverUser); driverRepository.saveAndFlush(driver);
        var driverAuth = authenticationFor(driverUser, OrganizationRole.DRIVER);
        mockMvc.perform(get("/driver/demand").param("date", DAY.toString()).param("organizationId", other.org().getId().toString())
                .with(authentication(driverAuth))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No uploaded demand for this date")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-FOREIGN"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"scheduleFile\""))));
        for (String route : List.of("/upload", "/transportation-demand/upload")) {
            mockMvc.perform(multipart(route).file(demandFile("test.csv", "100,A,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"))
                    .with(authentication(driverAuth)).with(csrf())).andExpect(status().isForbidden());
            mockMvc.perform(multipart(route).file(demandFile("test.csv", "100,A,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"))
                    .with(authentication(authenticationFor(f.manager(), OrganizationRole.OWNER)))).andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> forecasts.upload(f.org().getId(), driverUser.getEmail(), demandFile("test.csv")))
                .isInstanceOf(AccessDeniedException.class);
        mockMvc.perform(get("/transportation-demand").with(authentication(driverAuth))).andExpect(status().isForbidden());
        mockMvc.perform(get("/transportation-demand")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/transportation-demand").param("date", DAY.toString()).param("organizationId", other.org().getId().toString())
                .with(authentication(authenticationFor(f.manager(), OrganizationRole.OWNER)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE-FOREIGN"))));
    }

    @Test void establishedUploadUrlWorksAndAssignmentsRemainRetired() throws Exception {
        var f = fixture("bookmarks"); var auth = authenticationFor(f.manager(), OrganizationRole.OWNER);
        mockMvc.perform(get("/upload").with(authentication(auth))).andExpect(redirectedUrl("/transportation-demand#upload"));
        mockMvc.perform(multipart("/upload").file(demandFile("legacy.csv", "100,A,10/24/2026 9:00 AM,10/24/2026 5:00 PM,Park/Ops,Staff"))
                .with(authentication(auth)).with(csrf())).andExpect(redirectedUrl("/transportation-demand?date=2026-10-24"));
        mockMvc.perform(post("/assignments/manual").with(authentication(auth)).with(csrf())).andExpect(status().isGone());
        assertThat(assignmentRepository.count()).isZero();
    }

    @Test void anonymousCountsSurviveRawScheduleCleanupAndExpireAfterTheHistoryWindow() {
        var f = fixture("retention");
        LocalDate current = LocalDate.now(ZoneOffset.UTC);
        String currentDate = current.format(DateTimeFormatter.ofPattern("M/d/yyyy"));
        String old = current.minusMonths(13).format(DateTimeFormatter.ofPattern("M/d/yyyy"));
        var oldImport = forecasts.upload(f.org().getId(), f.manager().getEmail(), demandFile("old.csv",
                "100,A," + old + " 9:00 AM," + old + " 5:00 PM,Park/Ops,Staff"));
        forecasts.upload(f.org().getId(), f.manager().getEmail(), demandFile("current.csv",
                "100,A," + currentDate + " 9:00 AM," + currentDate + " 5:00 PM,Park/Ops,Staff"));
        uploadedShiftRepository.deleteShiftRowsBefore(current.plusDays(1));
        assertThat(forecasts.page(f.org().getId(), current).available()).isTrue();
        forecasts.purgeExpiredForecasts();
        assertThat(forecasts.page(f.org().getId(), oldImport.getFirstShiftDate()).available()).isFalse();
        assertThat(forecasts.page(f.org().getId(), current).available()).isTrue();
    }

    private org.springframework.mock.web.MockMultipartFile demandFile(String filename, String... rows) {
        org.springframework.web.multipart.MultipartFile csv = csvFile(filename, rows);
        try {
            return new org.springframework.mock.web.MockMultipartFile("scheduleFile", filename, "text/csv", csv.getBytes());
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    private DemandForecastService.Window window(DemandForecastService.Forecast page, String period, String time) {
        return page.periods().stream().filter(p -> p.key().equals(period)).findFirst().orElseThrow().windows().stream()
                .filter(w -> w.start().toLocalTime().equals(LocalTime.parse(time))).findFirst().orElseThrow();
    }
    private record Fixture(Organization org, UserAccount manager) {}
}
