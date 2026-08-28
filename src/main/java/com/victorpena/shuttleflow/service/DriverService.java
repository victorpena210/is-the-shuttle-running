package com.victorpena.shuttleflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.victorpena.shuttleflow.model.Driver;
import com.victorpena.shuttleflow.model.Organization;
import com.victorpena.shuttleflow.repository.DriverRepository;
import com.victorpena.shuttleflow.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class DriverService {

    private final DriverRepository driverRepository;
    private final OrganizationRepository organizationRepository;

    public DriverService(
            DriverRepository driverRepository,
            OrganizationRepository organizationRepository) {

        this.driverRepository = driverRepository;
        this.organizationRepository = organizationRepository;
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

    @Transactional
    public Driver registerDriver(
            Long organizationId,
            String employeeId,
            String name,
            boolean active) {

        String cleanedEmployeeId = employeeId.trim();
        String cleanedName = name.trim();

        if (cleanedEmployeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee ID is required."
            );
        }

        if (cleanedName.isBlank()) {
            throw new IllegalArgumentException(
                    "Driver name is required."
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
                cleanedName,
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

        /*
         * No explicit save is required here.
         * The driver is managed by JPA, so dirty checking
         * writes the changed active status at commit time.
         */
        return driver;
    }
}