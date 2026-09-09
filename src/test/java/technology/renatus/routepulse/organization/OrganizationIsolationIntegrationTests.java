package technology.renatus.routepulse.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationMembership;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class OrganizationIsolationIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void organizationACannotAccessOrganizationBsRecords() {
        Organization organizationA = createOrganization(
                "Organization A",
                "organization-a"
        );

        Organization organizationB = createOrganization(
                "Organization B",
                "organization-b"
        );

        UserAccount ownerA = createUser(
                "owner-a@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organizationA,
                        ownerA,
                        OrganizationRole.OWNER
                )
        );

        Driver driverA = driverRepository.save(
                new Driver(
                        organizationA,
                        "100",
                        "Driver A",
                        true
                )
        );

        Driver driverB = driverRepository.save(
                new Driver(
                        organizationB,
                        "100",
                        "Driver B",
                        true
                )
        );

        Authentication authenticationA = authenticationFor(
                ownerA,
                OrganizationRole.OWNER
        );

        Long currentOrganizationId =
                currentOrganizationService
                        .requireOrganizationId(authenticationA);

        assertEquals(
                organizationA.getId(),
                currentOrganizationId
        );

        assertEquals(
                List.of(driverA.getId()),
                driverService
                        .getAllDrivers(currentOrganizationId)
                        .stream()
                        .map(Driver::getId)
                        .toList()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> driverService.updateActiveStatus(
                        currentOrganizationId,
                        driverB.getId(),
                        false
                )
        );

        DriverAssignment organizationBAssignment =
                assignmentService.createManualAssignment(
                        organizationB.getId(),
                        LocalDate.of(2026, 9, 20),
                        ShuttlePeriod.MORNING,
                        driverB.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "Organization B coverage"
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.updateScheduledTimes(
                        currentOrganizationId,
                        organizationBAssignment.getId(),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)
                )
        );
    }
}
