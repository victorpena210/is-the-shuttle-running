package technology.renatus.routepulse.pickup;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupOutcomeRepository extends JpaRepository<PickupOutcome,Long> {
    Optional<PickupOutcome> findByOrganizationIdAndRiderIdAndClockOut(Long org,Long rider,Instant time);
    List<PickupOutcome> findAllByOrganizationIdAndClockOutGreaterThanEqualAndClockOutLessThan(Long org,Instant from,Instant to);
}
