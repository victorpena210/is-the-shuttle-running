package technology.renatus.routepulse.assignments.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;

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
            ORDER BY assignment.id
            """)
    List<DriverAssignment> findAllForUpdateByOrganizationAndDate(
            @Param("organizationId") Long organizationId,
            @Param("assignmentDate") LocalDate assignmentDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM DriverAssignment assignment
            WHERE assignment.id = :assignmentId
              AND assignment.organization.id = :organizationId
            """)
    Optional<DriverAssignment> findByIdAndOrganizationForUpdate(
            @Param("assignmentId") Long assignmentId,
            @Param("organizationId") Long organizationId
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

    List<DriverAssignment>
            findAllByOrganization_IdAndScheduledDriver_IdAndAssignmentDate(
                    Long organizationId,
                    Long driverId,
                    LocalDate assignmentDate
            );

    List<DriverAssignment>
            findAllByOrganization_IdAndScheduledDriver_IdOrderByAssignmentDateDesc(
                    Long organizationId,
                    Long driverId
            );

    long countByOrganization_IdAndScheduledDriver_IdAndAssignmentDateBefore(
            Long organizationId,
            Long driverId,
            LocalDate assignmentDate
    );

    @Query("""
            SELECT DISTINCT assignment
            FROM DriverAssignment assignment
            LEFT JOIN assignment.drivingSessions session
            WHERE assignment.organization.id = :organizationId
              AND assignment.assignmentDate = :assignmentDate
              AND (
                    assignment.scheduledDriver.id = :driverId
                    OR session.driver.id = :driverId
                  )
            """)
    List<DriverAssignment> findAllVisibleToDriverOnDate(
            @Param("organizationId") Long organizationId,
            @Param("driverId") Long driverId,
            @Param("assignmentDate") LocalDate assignmentDate
    );

    @Query("""
            SELECT DISTINCT assignment
            FROM DriverAssignment assignment
            LEFT JOIN assignment.drivingSessions session
            WHERE assignment.organization.id = :organizationId
              AND (
                    assignment.scheduledDriver.id = :driverId
                    OR session.driver.id = :driverId
                  )
            """)
    List<DriverAssignment> findAllVisibleToDriver(
            @Param("organizationId") Long organizationId,
            @Param("driverId") Long driverId
    );
    List<DriverAssignment> findAllByOrganization_IdAndAssignmentDateBetween(
            Long organizationId, LocalDate fromDate, LocalDate toDate);
}
