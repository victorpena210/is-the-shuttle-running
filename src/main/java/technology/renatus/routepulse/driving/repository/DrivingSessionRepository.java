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

    interface LastDriven { Long getDriverId(); Instant getStartedAt(); }

    @Query("select s.driver.id as driverId, max(s.startedAt) as startedAt from DrivingSession s where s.organization.id = :org and s.startedAt < :before group by s.driver.id")
    List<LastDriven> lastDriven(@Param("org") Long org, @Param("before") Instant before);

    Optional<DrivingSession> findByIdAndOrganization_Id(Long id, Long organizationId);

    long countByOrganization_IdAndDurationReviewRequiredTrueAndTimeReviewedAtIsNull(Long organizationId);

    List<DrivingSession> findTop50ByOrganization_IdAndDurationReviewRequiredTrueAndTimeReviewedAtIsNullOrderByStartedAtAsc(Long organizationId);

    @Query("""
            SELECT COUNT(s) FROM DrivingSession s WHERE s.organization.id = :organizationId
              AND s.driver.id = :driverId AND s.id <> :sessionId
              AND s.startedAt < :end AND (s.endedAt IS NULL OR s.endedAt > :start)
            """)
    long countOverlappingDriverSessions(@Param("organizationId") Long organizationId,
            @Param("driverId") Long driverId, @Param("sessionId") Long sessionId,
            @Param("start") Instant start, @Param("end") Instant end);

    long countByOrganization_IdAndEndReasonAndInactivityReviewedAtIsNull(Long organizationId,
            technology.renatus.routepulse.driving.domain.DrivingSessionEndReason endReason);

    @Query("SELECT DISTINCT s.organization.id FROM DrivingSession s WHERE s.endedAt IS NULL AND s.movement.startedAt IS NOT NULL")
    List<Long> findMonitoredOrganizationIds();

    @Query("""
            SELECT DISTINCT s.organization.id FROM DrivingSession s
            WHERE s.endedAt IS NULL AND s.startedAt <= :startedBefore
            """)
    List<Long> findOverdueOrganizationIds(@Param("startedBefore") Instant startedBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DrivingSession s WHERE s.id = :id AND s.organization.id = :organizationId")
    Optional<DrivingSession> findForReview(@Param("id") Long id, @Param("organizationId") Long organizationId);

    List<DrivingSession> findTop50ByOrganization_IdAndEndReasonAndInactivityReviewedAtIsNullOrderByEndedAtDesc(
            Long organizationId, technology.renatus.routepulse.driving.domain.DrivingSessionEndReason endReason);

    Optional<DrivingSession> findFirstByOrganization_IdAndEndedAtIsNotNullOrderByEndedAtDesc(Long organizationId);

    @Query("""
            SELECT session FROM DrivingSession session
            LEFT JOIN FETCH session.assignment assignment
            LEFT JOIN FETCH assignment.scheduledDriver
            WHERE session.organization.id = :organizationId
              AND session.driver.id = :driverId
              AND session.startedAt < :end
              AND (session.endedAt IS NULL OR session.endedAt > :start)
            ORDER BY session.startedAt DESC
            """)
    List<DrivingSession> findPersonalAnalyticsSessions(
            @Param("organizationId") Long organizationId,
            @Param("driverId") Long driverId,
            @Param("start") Instant start, @Param("end") Instant end);

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

    List<DrivingSession> findTop20ByOrganization_IdAndDriver_IdOrderByStartedAtDesc(
            Long organizationId, Long driverId);

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
