package technology.renatus.routepulse.scheduling.automatic;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrivingReminderRepository extends JpaRepository<DrivingReminderDelivery, Long> {
    Optional<DrivingReminderDelivery> findByOrganizationIdAndDriverIdAndWorkStartsAt(Long org, Long driver, Instant start);
}
