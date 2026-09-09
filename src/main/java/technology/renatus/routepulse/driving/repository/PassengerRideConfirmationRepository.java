package technology.renatus.routepulse.driving.repository;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;

public interface PassengerRideConfirmationRepository
        extends JpaRepository<
                PassengerRideConfirmation,
                Long> {

    boolean existsByAssignment_IdAndDeviceTokenHashAndSubmittedAtAfter(
            Long assignmentId,
            String deviceTokenHash,
            LocalDateTime submittedAfter
    );
}
