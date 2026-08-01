package com.victorpena.shuttleflow.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.DriverAssignment;

public interface DriverAssignmentRepository
        extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment> findAllByAssignmentDate(
            LocalDate assignmentDate);

    long countByDriver_IdAndAssignmentDateBefore(
            Long driverId,
            LocalDate assignmentDate);
}