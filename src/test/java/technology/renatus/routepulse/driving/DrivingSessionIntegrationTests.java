package technology.renatus.routepulse.driving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class DrivingSessionIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void startAndEndDrivingEnforceTheLifecycleRules() {
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

        String csvDate = today.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        csvScheduleProvider.loadSchedule(organization.getId(), operator.getEmail(), csvFile("wardrobe.csv",
                "100,First Driver," + csvDate + " 8:00 AM," + csvDate + " 5:00 PM,Park/Ops/Wardrobe,Staff",
                "200,Second Driver," + csvDate + " 8:00 AM," + csvDate + " 5:00 PM,Park/Ops/Wardrobe,Staff"));

        DriverAssignment assignment =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        today,
                        ShuttlePeriod.MORNING,
                        firstDriver.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
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
                        LocalTime.of(8, 30),
                        LocalTime.of(10, 30)
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
