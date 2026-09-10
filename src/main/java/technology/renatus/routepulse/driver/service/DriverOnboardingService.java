package technology.renatus.routepulse.driver.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.organization.service.OrganizationInvitationService;

@Service
public class DriverOnboardingService {

    private final CurrentOrganizationService
            currentOrganizationService;

    private final DriverService
            driverService;

    private final OrganizationInvitationService
            organizationInvitationService;

    public DriverOnboardingService(
            CurrentOrganizationService
                    currentOrganizationService,
            DriverService driverService,
            OrganizationInvitationService
                    organizationInvitationService) {

        this.currentOrganizationService =
                currentOrganizationService;

        this.driverService =
                driverService;

        this.organizationInvitationService =
                organizationInvitationService;
    }

    @Transactional
    public Driver registerAndInviteDriver(
            Authentication authentication,
            String employeeId,
            String email,
            boolean active) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        Driver driver =
                driverService.registerDriver(
                        organizationId,
                        employeeId,
                        email,
                        active
                );

        organizationInvitationService
                .createAndSendDriverInvitation(
                        authentication,
                        driver
                );

        return driver;
    }

    @Transactional
    public Driver updateDriverEmailAndResendInvitation(
            Authentication authentication,
            Long driverId,
            String email) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        Driver driver =
                driverService
                        .updatePendingInvitationEmail(
                                organizationId,
                                driverId,
                                email
                        );

        organizationInvitationService
                .replaceAndSendDriverInvitation(
                        authentication,
                        driver
                );

        return driver;
    }

    @Transactional
    public void resendDriverInvitation(
            Authentication authentication,
            Long driverId) {

        Long organizationId =
                currentOrganizationService
                        .requireOrganizationId(
                                authentication
                        );

        Driver driver =
                driverService.getDriver(
                        organizationId,
                        driverId
                );

        organizationInvitationService
                .createAndSendDriverInvitation(
                        authentication,
                        driver
                );
    }
}
