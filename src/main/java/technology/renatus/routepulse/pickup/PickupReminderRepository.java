package technology.renatus.routepulse.pickup;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PickupReminderRepository extends JpaRepository<PickupReminderDelivery,Long> {
    Optional<PickupReminderDelivery> findByOrganizationIdAndRiderIdAndClockOutAndDriverId(Long org,Long rider,Instant time,Long driver);
}
