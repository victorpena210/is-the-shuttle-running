package com.victorpena.shuttleflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.Driver;

public interface DriverRepository
        extends JpaRepository<Driver, Long> {

    boolean existsByOrganization_IdAndEmployeeId(
            Long organizationId,
            String employeeId
    );

    List<Driver> findAllByOrganization_IdOrderByNameAsc(
            Long organizationId
    );

    List<Driver>
            findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                    Long organizationId
            );

    Optional<Driver> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );
}