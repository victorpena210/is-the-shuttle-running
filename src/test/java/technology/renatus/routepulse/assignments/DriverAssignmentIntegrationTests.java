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

        ScheduleImport sourceImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "drivers.csv",
                                "100,Alice," + csvDate + " 8:00 AM," + csvDate + " 4:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver",
                                "200,Bob," + csvDate + " 8:30 AM," + csvDate + " 4:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
                        )
                );

        List<DriverAssignment> automaticAssignments =
                assignmentService.getOrCreateAssignments(
                        organization.getId(),
                        assignmentDate
                );

        // The number of automatically-created periods is demand-dependent.
        // This test verifies the audit semantics of the morning automatic assignment.
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
        assertTrue(List.of(alice.getId(), bob.getId()).contains(originalSuggestionId));

        assertEquals(
                sourceImport.getId(),
                morning.getSourceScheduleImport().getId()
        );

        DriverAssignment overridden =
                assignmentService.changeAssignedDriver(
                        organization.getId(),
                        morning.getId(),
                        charlie.getId(),
                        "Manager coverage change",
                        uploader.getEmail()
                );

        assertEquals(
                AssignmentSource.OVERRIDE,
                overridden.getAssignmentSource()
        );

        assertEquals(
                charlie.getId(),
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
                        LocalTime.of(15, 0),
                        LocalTime.of(17, 0),
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
                                "200,Bob," + csvDate + " 9:30 AM," + csvDate + " 5:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
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
    void managerCanAssignOneDriverToBothShuttlePeriods() {
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
                LocalDate.of(2026, 9, 18);

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
