package technology.renatus.routepulse.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import technology.renatus.routepulse.model.PassengerRideConfirmation;

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