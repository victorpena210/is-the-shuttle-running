package com.victorpena.shuttleflow.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.DriverAssignment;

public interface DriverAssignmentRepository
        extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment>
            findAllByOrganization_IdAndAssignmentDate(
                    Long organizationId,
                    LocalDate assignmentDate
            );

    long countByOrganization_IdAndDriver_IdAndAssignmentDateBefore(
            Long organizationId,
            Long driverId,
            LocalDate assignmentDate
    );
}