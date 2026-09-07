package technology.renatus.routepulse.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import technology.renatus.routepulse.model.DrivingSession;

public interface DrivingSessionRepository
        extends JpaRepository<DrivingSession, Long> {

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
