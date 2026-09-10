package technology.renatus.routepulse.driving.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;

public interface PassengerRideConfirmationRepository
        extends JpaRepository<
                PassengerRideConfirmation,
                Long> {

    boolean existsByAssignment_IdAndDeviceTokenHashAndSubmittedAtAfter(
            Long assignmentId,
            String deviceTokenHash,
            Instant submittedAfter
    );

    long countByOrganization_IdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
            Long organizationId,
            Instant submittedAtStart,
            Instant submittedAtEndExclusive
    );

    @Query("""
            SELECT confirmation
            FROM PassengerRideConfirmation confirmation
            WHERE confirmation.organization.id = :organizationId
              AND confirmation.submittedAt >= :start
              AND confirmation.submittedAt < :end
            ORDER BY confirmation.submittedAt ASC
            """)
    List<PassengerRideConfirmation> findAllForServiceVerification(
            @Param("organizationId") Long organizationId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
