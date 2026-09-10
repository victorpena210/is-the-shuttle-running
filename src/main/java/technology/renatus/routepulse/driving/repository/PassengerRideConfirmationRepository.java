package technology.renatus.routepulse.driving.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
