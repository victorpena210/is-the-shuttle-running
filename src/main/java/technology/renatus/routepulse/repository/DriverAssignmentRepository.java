package technology.renatus.routepulse.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.ShuttlePeriod;

public interface DriverAssignmentRepository
        extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment>
            findAllByOrganization_IdAndAssignmentDate(
                    Long organizationId,
                    LocalDate assignmentDate
            );

    Optional<DriverAssignment>
            findByIdAndOrganization_Id(
                    Long assignmentId,
                    Long organizationId
            );

    Optional<DriverAssignment>
            findByOrganization_IdAndAssignmentDateAndPeriod(
                    Long organizationId,
                    LocalDate assignmentDate,
                    ShuttlePeriod period
            );

    long countByOrganization_IdAndDriver_IdAndAssignmentDateBefore(
            Long organizationId,
            Long driverId,
            LocalDate assignmentDate
    );
}
