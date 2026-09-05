package technology.renatus.routepulse.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.Driver;

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