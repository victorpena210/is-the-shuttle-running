package technology.renatus.routepulse.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.OrganizationRepository;
import technology.renatus.routepulse.repository.UploadedShiftRepository;

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

        this.driverRepository = driverRepository;
        this.organizationRepository = organizationRepository;
        this.uploadedShiftRepository = uploadedShiftRepository;
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

    public String findEmployeeName(
            Long organizationId,
            String employeeId) {

        String cleanedEmployeeId = employeeId == null
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
                        uploadedShift.getEmployeeName().trim()
                )
                .filter(name -> !name.isBlank())
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
            boolean active) {

        String cleanedEmployeeId = employeeId == null
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

        if (driverRepository
                .existsByOrganization_IdAndEmployeeId(
                        organizationId,
                        cleanedEmployeeId
                )) {

            throw new IllegalArgumentException(
                    "A driver with that employee ID already exists "
                            + "in this organization."
            );
        }

        String employeeName = findEmployeeName(
                organizationId,
                cleanedEmployeeId
        );

        if (employeeName.length() > 200) {
            throw new IllegalArgumentException(
                    "Driver name cannot exceed 200 characters."
            );
        }

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Organization was not found."
                                )
                        );

        Driver driver = new Driver(
                organization,
                cleanedEmployeeId,
                employeeName,
                active
        );

        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateActiveStatus(
            Long organizationId,
            Long id,
            boolean active) {

        Driver driver = driverRepository
                .findByIdAndOrganization_Id(
                        id,
                        organizationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Driver was not found."
                        )
                );

        driver.setActive(active);

        return driver;
    }
}