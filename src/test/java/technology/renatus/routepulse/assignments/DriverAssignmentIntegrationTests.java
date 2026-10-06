package technology.renatus.routepulse.assignments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DriverAssignmentIntegrationTests extends MySqlIntegrationTestSupport {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private technology.renatus.routepulse.calendar.service.ParkOperatingDayService operatingDays;

    @Test
    void automaticManualAndOverrideAssignmentsPreserveTheirAuditMeaning() {
        Organization organization = createOrganization(
                "Assignment Organization",
                "assignment-organization"
        );

        UserAccount uploader = createUser(
                "assignments@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        uploader,
                        OrganizationRole.ADMIN
                )
        );

        Driver alice = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "Alice",
                        true
                )
        );

        Driver bob = driverRepository.save(
                new Driver(
                        organization,
                        "200",
                        "Bob",
                        true
                )
        );

        Driver charlie = driverRepository.save(
                new Driver(
                        organization,
                        "300",
                        "Charlie",
                        true
                )
        );

        LocalDate assignmentDate =
                LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1);
        String csvDate = assignmentDate.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        org.mockito.Mockito.when(operatingDays.getOperatingDay(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> new technology.renatus.routepulse.calendar.domain.ParkOperatingDay(
                        invocation.getArgument(0),
                        technology.renatus.routepulse.calendar.domain.ParkOperatingStatus.OPEN,
                        LocalTime.of(9, 0), LocalTime.of(21, 0), false, "TEST", null));

        // The drivers clock in before the 09:00 morning-demand analysis window.
        // Include an employee arrival inside that window so the real planner
        // has demand to cover; driver availability alone is not demand.
        ScheduleImport sourceImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "drivers.csv",
                                "100,Alice," + csvDate + " 8:00 AM," + csvDate + " 4:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver",
                                "200,Bob," + csvDate + " 8:30 AM," + csvDate + " 4:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver",
                                "300,Charlie," + csvDate + " 8:00 AM," + csvDate + " 4:00 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver",
                                "400,Rider," + csvDate + " 9:00 AM," + csvDate + " 5:00 PM,Park/Ops/Rides/101 North Gate,Operator"
                        )
                );

        List<DriverAssignment> automaticAssignments =
                assignmentService.getOrCreateAssignments(
                        organization.getId(),
                        assignmentDate
                );

        // The number of automatically-created periods is demand-dependent.
        // This test verifies the audit semantics of the morning automatic assignment.
        assertTrue(
                automaticAssignments.stream().anyMatch(assignment ->
                        assignment.getPeriod() == ShuttlePeriod.MORNING),
                "The 9:00 AM employee arrival and available drivers must produce morning coverage."
        );
        DriverAssignment morning = assignmentFor(
                automaticAssignments,
                ShuttlePeriod.MORNING
        );

        assertEquals(
                AssignmentSource.AUTO,
                morning.getAssignmentSource()
        );

        assertEquals(
                morning.getOriginallySuggestedDriver().getId(),
                morning.getDriver().getId()
        );

        Long originalSuggestionId = morning.getOriginallySuggestedDriver().getId();
        assertTrue(List.of(alice.getId(), bob.getId(), charlie.getId()).contains(originalSuggestionId));
        Driver replacement = originalSuggestionId.equals(charlie.getId()) ? alice : charlie;

        assertEquals(
                sourceImport.getId(),
                morning.getSourceScheduleImport().getId()
        );

        DriverAssignment overridden =
                assignmentService.changeAssignedDriver(
                        organization.getId(),
                        morning.getId(),
                        replacement.getId(),
                        "Manager coverage change",
                        uploader.getEmail()
                );

        assertEquals(
                AssignmentSource.OVERRIDE,
                overridden.getAssignmentSource()
        );

        assertEquals(
                replacement.getId(),
                overridden.getFinalDriver().getId()
        );

        assertEquals(
                originalSuggestionId,
                overridden
                        .getOriginallySuggestedDriver()
                        .getId()
        );

        assertEquals(
                "Manager coverage change",
                overridden.getChangeReason()
        );

        assertNotNull(overridden.getChangedAt());

        assertEquals(
                uploader.getId(),
                overridden.getChangedBy().getId()
        );

        DriverAssignment manual =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate.plusDays(1),
                        ShuttlePeriod.AFTERNOON,
                        bob.getId(),
                        LocalTime.of(19, 0),
                        LocalTime.of(21, 0),
                        "No uploaded schedule"
                );

        assertEquals(
                AssignmentSource.MANUAL,
                manual.getAssignmentSource()
        );

        assertEquals(
                bob.getId(),
                manual.getFinalDriver().getId()
        );

        assertNull(
                manual.getOriginallySuggestedDriver()
        );

        assertEquals(
                120,
                manual.getScheduledMinutes()
        );

        assertEquals(
                "No uploaded schedule",
                manual.getChangeReason()
        );

        ScheduleImport correctedImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "corrected-drivers.csv",
                                "100,Alice," + csvDate + " 9:00 AM," + csvDate + " 5:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver",
                                "200,Bob," + csvDate + " 9:30 AM," + csvDate + " 5:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver",
                                "400,Rider," + csvDate + " 9:00 AM," + csvDate + " 5:00 PM,Park/Ops/Rides/101 North Gate,Operator"
                        )
                );

        assertTrue(
                morning.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assertFalse(
                manual.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assignmentService
                .confirmAssignmentsAgainstLatestImport(
                        organization.getId(),
                        assignmentDate,
                        uploader.getEmail()
                );

        assertFalse(
                morning.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assertEquals(
                uploader.getId(),
                morning.getScheduleReviewedBy().getId()
        );
    }

    @Test
    void importedRosterStaffingAndOptionalMiddayAreEnforced() {
        Organization organization = createOrganization("Office Coverage", "office-coverage");
        UserAccount uploader = createUser("office@example.com", true);
        membershipRepository.save(new OrganizationMembership(organization, uploader, OrganizationRole.ADMIN));
        Driver first = driverRepository.save(new Driver(organization, "100", "First", true));
        Driver second = driverRepository.save(new Driver(organization, "200", "Second", true));
        driverRepository.save(new Driver(organization, "300", "Inactive", false));
        LocalDate date = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1);
        String day = date.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        org.mockito.Mockito.when(operatingDays.getOperatingDay(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> new technology.renatus.routepulse.calendar.domain.ParkOperatingDay(
                        invocation.getArgument(0), technology.renatus.routepulse.calendar.domain.ParkOperatingStatus.OPEN,
                        LocalTime.of(9, 0), LocalTime.of(21, 0), false, "TEST", null));
        String firstRow = "100,First," + day + " 9:00 AM," + day + " 9:00 PM,Park/Ops/Wardrobe,Staff";
        String inactiveRow = "300,Inactive," + day + " 9:00 AM," + day + " 9:00 PM,Park/Ops/Wardrobe,Staff";
        String riderRow = "400,Rider," + day + " 1:00 PM," + day + " 6:00 PM,Park/Ops/Rides,Operator";
        String nonRosterRow = "500,Other," + day + " 9:00 AM," + day + " 9:00 PM,Park/Ops/Wardrobe,Staff";
        csvScheduleProvider.loadSchedule(organization.getId(), uploader.getEmail(), csvFile("one.csv",
                firstRow, inactiveRow, riderRow, nonRosterRow));
        var one = assignmentService.getOrCreateAssignments(organization.getId(), date);
        assertTrue(one.stream().noneMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING),
                "Inactive and non-roster employees must not count as office backup.");
        assertTrue(one.stream().noneMatch(a -> a.getPeriod() == ShuttlePeriod.MIDDAY));

        csvScheduleProvider.loadSchedule(organization.getId(), uploader.getEmail(), csvFile("two.csv",
                firstRow, "200,Second," + day + " 9:00 AM," + day + " 9:00 PM,Park/Ops/Wardrobe,Staff", riderRow));
        var two = assignmentService.getOrCreateAssignments(organization.getId(), date);
        assertTrue(two.stream().anyMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING));
        assertTrue(two.stream().noneMatch(a -> a.getPeriod() == ShuttlePeriod.MIDDAY));
        assertNull(assignmentService.getMiddaySuggestion(organization.getId(), date),
                "The 1 PM midday demand is before the default 7 PM wardrobe closing time.");

        // Exercise optional midday acceptance on a day with an earlier office close.
        assignmentService.updateWardrobeHours(organization.getId(),
                technology.renatus.routepulse.wardrobe.WardrobeHoursRule.Scope.DAY,
                "Early closing for midday coverage test", date, date, LocalTime.NOON, uploader.getEmail());
        var suggestion = assignmentService.getMiddaySuggestion(organization.getId(), date);
        assertNotNull(suggestion);
        assertEquals(LocalTime.of(13, 0), suggestion.getScheduledStartTime(),
                "The suggestion starts at demand, after the noon wardrobe closing time.");
        assertNull(suggestion.getId());
        var accepted = assignmentService.acceptMiddaySuggestion(organization.getId(), date,
                technology.renatus.routepulse.assignments.service.AssignmentPlanningService.suggestionKey(suggestion));
        assertEquals(AssignmentSource.MANUAL, accepted.getAssignmentSource());
        assertEquals(suggestion.getPlannedDrivingWindows(), accepted.getPlannedDrivingWindows());
        assertNull(assignmentService.getMiddaySuggestion(organization.getId(), date));

        csvScheduleProvider.loadSchedule(organization.getId(), uploader.getEmail(), csvFile("one-again.csv", firstRow, riderRow));
        var revised = assignmentService.getOrCreateAssignments(organization.getId(), date);
        assertTrue(revised.stream().noneMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING));
        assertTrue(revised.stream().anyMatch(a -> a.getId().equals(accepted.getId())), "Accepted optional coverage must be preserved.");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                assignmentService.createManualAssignment(organization.getId(), date, ShuttlePeriod.MORNING,
                        first.getId(), LocalTime.of(9, 0), LocalTime.NOON, "Manual coverage"));
    }

    @Test
    void historicalManualRecordsCanPreserveOneDriverInBothPeriods() {
        Organization organization = createOrganization(
                "Emergency Coverage Organization",
                "emergency-coverage-organization"
        );

        Driver driver = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "Only Available Driver",
                        true
                )
        );

        LocalDate assignmentDate =
                LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);

        DriverAssignment morning =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate,
                        ShuttlePeriod.MORNING,
                        driver.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "Emergency coverage"
                );

        DriverAssignment afternoon =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate,
                        ShuttlePeriod.AFTERNOON,
                        driver.getId(),
                        LocalTime.of(15, 0),
                        LocalTime.of(17, 0),
                        "Emergency coverage"
                );

        assertEquals(
                morning.getDriver().getId(),
                afternoon.getDriver().getId()
        );
    }
}
