package technology.renatus.routepulse.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import technology.renatus.routepulse.model.DriverWorkloadEvent;
import technology.renatus.routepulse.model.DriverWorkloadEventType;

public interface DriverWorkloadEventRepository
        extends JpaRepository<DriverWorkloadEvent, Long> {

    List<DriverWorkloadEvent>
            findAllByOrganization_IdAndCreatedAtBeforeOrderByCreatedAtAsc(
                    Long organizationId,
                    LocalDateTime before
            );

    List<DriverWorkloadEvent>
            findAllByOrganization_IdAndDriver_IdOrderByCreatedAtAsc(
                    Long organizationId,
                    Long driverId
            );

    boolean existsByAssignment_IdAndEventType(
            Long assignmentId,
            DriverWorkloadEventType eventType
    );

    boolean existsBySession_IdAndEventType(
            Long sessionId,
            DriverWorkloadEventType eventType
    );
}
