package com.victorpena.shuttleflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.Driver;

public interface DriverRepository
        extends JpaRepository<Driver, Long> {

    boolean existsByEmployeeId(String employeeId);

    List<Driver> findAllByOrderByNameAsc();

    List<Driver> findAllByActiveTrueOrderByNameAsc();
}