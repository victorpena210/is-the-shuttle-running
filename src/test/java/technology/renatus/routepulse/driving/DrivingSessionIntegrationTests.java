package technology.renatus.routepulse.driving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingSessionIntegrationTests extends MySqlIntegrationTestSupport {

    @MockitoBean
    private ParkOperatingDayService operatingDays;

    @ParameterizedTest
    @ValueSource(ints = {11, 12, 17})
    void startAndEndDrivingEnforceTheLifecycleRules(int parkOpeningHour) {
        Organization organization = createOrganization(
                "Driving Organization",
                "driving-organization"
        );

        UserAccount operator = createUser(
                "driving-operator@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        operator,
                        OrganizationRole.ADMIN
                )
        );

        Driver firstDriver = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "First Driver",
                        true
                )
        );

        Driver secondDriver = driverRepository.save(
                new Driver(
                        organization,
                        "200",
                        "Second Driver",
                        true
                )
        );

        LocalDate today =
                assignmentService.getCurrentOperationsDate();

        // Exercise real opening/staffing validation without calling the live
        // park calendar. A Friday-style opening uses the midday service period.
        when(operatingDays.getOperatingDay(any())).thenAnswer(invocation ->
                new ParkOperatingDay(invocation.getArgument(0), ParkOperatingStatus.OPEN,
                        LocalTime.of(parkOpeningHour, 0), LocalTime.of(22, 0), false, "TEST", null));
        LocalTime serviceStart = LocalTime.of(parkOpeningHour == 17 ? 15 : 9, 0);
        LocalTime serviceEnd = serviceStart.plusHours(2);
        ShuttlePeriod openingPeriod = parkOpeningHour == 17 ? ShuttlePeriod.MIDDAY : ShuttlePeriod.MORNING;
        String riderArrival = parkOpeningHour == 17 ? "3:00 PM" : "9:00 AM";
        String csvDate = today.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        csvScheduleProvider.loadSchedule(organization.getId(), operator.getEmail(), csvFile("wardrobe.csv",
                "100,First Driver," + csvDate + " 8:00 AM," + csvDate + " 11:00 PM,Park/Ops/Wardrobe,Staff",
                "200,Second Driver," + csvDate + " 8:00 AM," + csvDate + " 11:00 PM,Park/Ops/Wardrobe,Staff",
                "300,Sample Rider," + csvDate + " " + riderArrival + "," + csvDate + " 9:00 PM,Park/Ops/Rides,Operator"));

        DriverAssignment assignment =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        today,
                        openingPeriod,
                        firstDriver.getId(),
                        serviceStart,
                        serviceEnd,
                        "Daily coverage"
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.endDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        assignmentService.startDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDriving());
        assertNotNull(assignment.getActualStartedAt());

        assertEquals(
                operator.getId(),
                assignment.getActualStartedBy().getId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.startDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.updateScheduledTimes(
                        organization.getId(),
                        assignment.getId(),
                        serviceStart.plusMinutes(30),
                        serviceEnd.plusMinutes(30)
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.changeAssignedDriver(
                        organization.getId(),
                        assignment.getId(),
                        secondDriver.getId(),
                        "Too late",
                        operator.getEmail()
                )
        );

        assignmentService.endDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDrivingCompleted());
        assertNotNull(assignment.getActualEndedAt());

        assertEquals(
                operator.getId(),
                assignment.getActualEndedBy().getId()
        );

        /*
         * Ending one actual session must not permanently close the scheduled
         * assignment. A manager can reopen it and RoutePulse keeps both
         * sessions under the same assignment.
         */
        assignmentService.startDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDriving());
        assertEquals(2, assignment.getDrivingSessions().size());

        assignmentService.endDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDrivingCompleted());
        assertEquals(2, assignment.getDrivingSessions().size());

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.endDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        DriverAssignment missingSchedule =
                assignmentRepository.save(
                        DriverAssignment.automatic(
                                today,
                                ShuttlePeriod.AFTERNOON,
                                secondDriver
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.startDriving(
                        organization.getId(),
                        missingSchedule.getId(),
                        operator.getEmail()
                )
        );
    }
}
