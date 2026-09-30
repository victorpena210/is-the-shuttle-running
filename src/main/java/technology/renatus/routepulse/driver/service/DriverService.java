package technology.renatus.routepulse.driver.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository driverRepository;
    private final OrganizationRepository organizationRepository;
    private final UploadedShiftRepository uploadedShiftRepository;

    public DriverService(
            DriverRepository driverRepository,
            OrganizationRepository organizationRepository,
            UploadedShiftRepository uploadedShiftRepository) {

        this.driverRepository =
                driverRepository;

        this.organizationRepository =
                organizationRepository;

        this.uploadedShiftRepository =
                uploadedShiftRepository;
    }

    public List<Driver> getAllDrivers(
            Long organizationId) {

        return driverRepository
                .findAllByOrganization_IdOrderByNameAsc(
                        organizationId
                );
    }

    public List<Driver> getActiveDrivers(
            Long organizationId) {

        return driverRepository
                .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                        organizationId
                );
    }

    public Driver getDriver(
            Long organizationId,
            Long driverId) {

        return driverRepository
        		.findByIdAndOrganization_Id(
                        driverId,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Driver was not found."
                        )
                );
    }

    public String findEmployeeName(
            Long organizationId,
            String employeeId) {

        String cleanedEmployeeId =
                employeeId == null
                        ? ""
                        : employeeId.trim();

        if (cleanedEmployeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee ID is required."
            );
        }

        if (cleanedEmployeeId.length() > 50) {
            throw new IllegalArgumentException(
                    "Employee ID cannot exceed 50 characters."
            );
        }

        return uploadedShiftRepository
                .findFirstByOrganization_IdAndEmployeeIdOrderByImportedAtDescIdDesc(
                        organizationId,
                        cleanedEmployeeId
                )
                .map(uploadedShift ->
                        uploadedShift
                                .getEmployeeName()
                                .trim()
                )
                .filter(name ->
                        !name.isBlank()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No employee with that ID was found in this organization's uploaded schedules."
                        )
                );
    }

    @Transactional
    public Driver registerDriver(
            Long organizationId,
            String employeeId,
            String email,
            boolean active) {

        String cleanedEmployeeId =
                employeeId == null
                        ? ""
                        : employeeId.trim();

        if (cleanedEmployeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee ID is required."
            );
        }

        if (cleanedEmployeeId.length() > 50) {
            throw new IllegalArgumentException(
                    "Employee ID cannot exceed 50 characters."
            );
        }

        String cleanedEmail =
                normalizeEmail(email);

        if (driverRepository
                .existsByOrganization_IdAndEmployeeId(
                        organizationId,
                        cleanedEmployeeId
                )) {

            throw new IllegalArgumentException(
                    "A driver with that employee ID already exists in this organization."
            );
        }

        if (driverRepository
                .existsByOrganization_IdAndEmailIgnoreCase(
                        organizationId,
                        cleanedEmail
                )) {

            throw new IllegalArgumentException(
                    "A driver with that email address already exists in this organization."
            );
        }

        String employeeName =
                findEmployeeName(
                        organizationId,
                        cleanedEmployeeId
                );

        if (employeeName.length() > 200) {
            throw new IllegalArgumentException(
                    "Driver name cannot exceed 200 characters."
            );
        }

        Organization organization =
                organizationRepository
                        .findById(
                                organizationId
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Organization was not found."
                                )
                        );

        Driver driver =
                new Driver(
                        organization,
                        cleanedEmployeeId,
                        employeeName,
                        cleanedEmail,
                        active
                );

        return driverRepository.save(
                driver
        );
    }

    @Transactional
    public Driver updatePendingInvitationEmail(
            Long organizationId,
            Long id,
            String email) {

        Driver driver =
                getDriver(
                        organizationId,
                        id
                );

        if (driver.hasAccount()) {
            throw new IllegalStateException(
                    "The driver's email cannot be changed here because their Is the Shuttle Running account is already active."
            );
        }

        String cleanedEmail =
                normalizeEmail(email);

        if (driverRepository
                .existsByOrganization_IdAndEmailIgnoreCaseAndIdNot(
                        organizationId,
                        cleanedEmail,
                        id
                )) {

            throw new IllegalArgumentException(
                    "A driver with that email address already exists in this organization."
            );
        }

        driver.changeEmail(
                cleanedEmail
        );

        return driver;
    }

    @Transactional
    public Driver updateActiveStatus(
            Long organizationId,
            Long id,
            boolean active) {

        Driver driver =
                getDriver(
                        organizationId,
                        id
                );

        driver.setActive(active);

        return driver;
    }

    private String normalizeEmail(
            String email) {

        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "Driver email address is required."
            );
        }

        String cleanedEmail =
                email.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (cleanedEmail.length() > 254) {
            throw new IllegalArgumentException(
                    "Email address cannot exceed 254 characters."
            );
        }

        try {

            InternetAddress address =
                    new InternetAddress(
                            cleanedEmail,
                            true
                    );

            if (!cleanedEmail.equalsIgnoreCase(
                    address.getAddress()
            )) {

                throw new AddressException();
            }

        } catch (AddressException exception) {

            throw new IllegalArgumentException(
                    "Enter a valid driver email address."
            );
        }

        return cleanedEmail;
    }
}
