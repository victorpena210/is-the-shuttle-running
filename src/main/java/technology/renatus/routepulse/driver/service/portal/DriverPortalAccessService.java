package technology.renatus.routepulse.driver.service.portal;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

/** Resolves and validates the signed-in driver for driver-portal operations. */
@Service
@Transactional(readOnly = true)
public class DriverPortalAccessService {

    private final CurrentOrganizationService currentOrganizationService;
    private final UserAccountRepository userAccountRepository;
    private final DriverRepository driverRepository;

    public DriverPortalAccessService(
            CurrentOrganizationService currentOrganizationService,
            UserAccountRepository userAccountRepository,
            DriverRepository driverRepository) {
        this.currentOrganizationService = currentOrganizationService;
        this.userAccountRepository = userAccountRepository;
        this.driverRepository = driverRepository;
    }

    public Driver requireCurrentDriver(Authentication authentication) {
        Organization organization = currentOrganizationService.requireOrganization(authentication);
        UserAccount user = requireCurrentUser(authentication);

        Driver driver = driverRepository
                .findByOrganization_IdAndUser_Id(organization.getId(), user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "This account is not linked to a driver profile."));

        // Initialize the organization name while the read transaction is open.
        driver.getOrganization().getDisplayName();
        return driver;
    }

    public UserAccount requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("A signed-in driver account is required.");
        }

        return userAccountRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "The signed-in account could not be found."));
    }

    public void requireActiveDriver(Driver driver) {
        if (!driver.isActive()) {
            throw new IllegalStateException(
                    "Your driver profile is currently inactive. "
                            + "A manager must reactivate it before you can record driving.");
        }
    }

    public void validateCheckpointCode(Organization organization, String checkpointCode) {
        String activeCheckpointCode = organization.getDrivingQrCode();

        if (checkpointCode == null
                || checkpointCode.isBlank()
                || activeCheckpointCode == null
                || activeCheckpointCode.isBlank()
                || !activeCheckpointCode.equals(checkpointCode.trim())) {
            throw new IllegalArgumentException(
                    "This shuttle checkpoint is not valid for your organization.");
        }
    }
}
