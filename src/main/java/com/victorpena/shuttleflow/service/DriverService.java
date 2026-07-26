//package com.victorpena.shuttleflow.service;
//
//import java.util.List;
//
//import org.springframework.stereotype.Service;
//
//import com.victorpena.shuttleflow.model.Driver;
//import com.victorpena.shuttleflow.repository.DriverRepository;
//
//@Service
//public class DriverService {
//	
//    private final DriverRepository driverRepository;
//
//    public DriverService(
//            DriverRepository driverRepository) {
//
//        this.driverRepository = driverRepository;
//    }	
//    
//    public List<Driver> getAllDrivers() {
//        return driverRepository.findAllByOrderByNameAsc();
//    }
//    
//    public Driver registerDriver(
//            String employeeId,
//            String name,
//            boolean active) {
//
//        String cleanedEmployeeId = employeeId.trim();
//        String cleanedName = name.trim();
//
//        if (cleanedEmployeeId.isBlank()) {
//            throw new IllegalArgumentException(
//                    "Employee ID is required."
//            );
//        }
//
//        if (cleanedName.isBlank()) {
//            throw new IllegalArgumentException(
//                    "Driver name is required."
//            );
//        }
//
//        if (driverRepository.existsByEmployeeId(
//                cleanedEmployeeId)) {
//
//            throw new IllegalArgumentException(
//                    "A driver with that employee ID already exists."
//            );
//        }
//
//        Driver driver = new Driver(
//                cleanedEmployeeId,
//                cleanedName,
//                active
//        );
//
//        return driverRepository.save(driver);
//    }
//    
//    public Driver updateActiveStatus(
//            Long id,
//            boolean active) {
//
//        Driver driver = driverRepository.findById(id)
//                .orElseThrow(() ->
//                        new IllegalArgumentException(
//                                "Driver was not found."
//                        )
//                );
//
//        driver.setActive(active);
//
//        return driverRepository.save(driver);
//    }
//    
//}
