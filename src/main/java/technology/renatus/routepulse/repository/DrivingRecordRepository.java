package technology.renatus.routepulse.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import technology.renatus.routepulse.model.DriverAssignment;

public interface DrivingRecordRepository
        extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment>
            findAllByOrganization_IdAndAssignmentDateBetween(
                    Long organizationId,
                    LocalDate fromDate,
                    LocalDate toDate
            );

    List<DriverAssignment>
            findAllByOrganization_IdAndAssignmentDateBetweenAndDriver_Id(
                    Long organizationId,
                    LocalDate fromDate,
                    LocalDate toDate,
                    Long driverId
            );
}