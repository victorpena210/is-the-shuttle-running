package technology.renatus.routepulse.driving.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.driving.model.DrivingSession;

public interface DrivingSessionRepository
        extends JpaRepository<DrivingSession, Long> {

    @Query("""
            SELECT session FROM DrivingSession session
            JOIN FETCH session.driver
            WHERE session.organization.id = :organizationId
              AND session.startedAt < :end
              AND (session.endedAt IS NULL OR session.endedAt > :start)
            ORDER BY session.startedAt
            """)
    List<DrivingSession> findOverlappingForServiceReview(
            @Param("organizationId") Long organizationId,
            @Param("start") Instant start, @Param("end") Instant end);

    List<DrivingSession> findAllByOrganization_IdAndAssignmentIsNullAndEndedAtIsNotNullAndStartedAtBefore(
            Long organizationId, Instant before);

    @Query("""
            SELECT session FROM DrivingSession session
            WHERE session.organization.id = :organizationId AND session.assignment IS NULL
              AND session.startedAt >= :start AND session.startedAt < :end
              AND (:driverId IS NULL OR session.driver.id = :driverId)
            ORDER BY session.startedAt DESC
            """)
    List<DrivingSession> findUnscheduledRecords(
            @Param("organizationId") Long organizationId, @Param("driverId") Long driverId,
            @Param("start") Instant start, @Param("end") Instant end);

    List<DrivingSession> findTop20ByOrganization_IdAndDriver_IdAndAssignmentIsNullOrderByStartedAtDesc(
            Long organizationId, Long driverId);

    List<DrivingSession> findAllByAssignment_IdOrderByStartedAtAsc(
            Long assignmentId
    );

    Optional<DrivingSession>
            findFirstByOrganization_IdAndEndedAtIsNullOrderByStartedAtDesc(
                    Long organizationId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT session
            FROM DrivingSession session
            WHERE session.organization.id = :organizationId
              AND session.endedAt IS NULL
            ORDER BY session.startedAt DESC
            """)
    List<DrivingSession> findActiveForUpdateByOrganization(
            @Param("organizationId") Long organizationId
    );

    Optional<DrivingSession>
            findFirstByAssignment_IdAndEndedAtIsNullOrderByStartedAtDesc(
                    Long assignmentId
            );

    List<DrivingSession>
            findAllByOrganization_IdAndEndedAtIsNotNullAndAssignment_AssignmentDateBefore(
                    Long organizationId,
                    LocalDate assignmentDate
            );
}
