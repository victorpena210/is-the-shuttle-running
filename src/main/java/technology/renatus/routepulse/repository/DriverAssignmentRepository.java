package technology.renatus.routepulse.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.ShuttlePeriod;

public interface DriverAssignmentRepository
        extends JpaRepository<DriverAssignment, Long> {

    List<DriverAssignment>
            findAllByOrganization_IdAndAssignmentDate(
                    Long organizationId,
                    LocalDate assignmentDate
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM DriverAssignment assignment
            WHERE assignment.organization.id = :organizationId
              AND assignment.assignmentDate = :assignmentDate
            """)
    List<DriverAssignment>
            findAllForUpdateByOrganizationAndDate(
                    @Param("organizationId")
                    Long organizationId,
                    @Param("assignmentDate")
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

    Optional<DriverAssignment>
            findFirstByOrganization_IdAndAssignmentDateAndActualStartedAtIsNotNullAndActualEndedAtIsNullOrderByActualStartedAtDesc(
                    Long organizationId,
                    LocalDate assignmentDate
            );

    List<DriverAssignment>
            findAllByOrganization_IdAndDriver_IdAndAssignmentDate(
                    Long organizationId,
                    Long driverId,
                    LocalDate assignmentDate
            );

    List<DriverAssignment>
            findAllByOrganization_IdAndDriver_IdOrderByAssignmentDateDesc(
                    Long organizationId,
                    Long driverId
            );

    long countByOrganization_IdAndDriver_IdAndAssignmentDateBefore(
            Long organizationId,
            Long driverId,
            LocalDate assignmentDate
    );
}