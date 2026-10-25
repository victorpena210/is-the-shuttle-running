package technology.renatus.routepulse.pickup;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PickupRiderRepository extends JpaRepository<PickupRider,Long> {
    List<PickupRider> findAllByOrganizationIdOrderByName(Long org);
    Optional<PickupRider> findByIdAndOrganizationId(Long id,Long org);
    Optional<PickupRider> findByOrganizationIdAndFeedFingerprint(Long org,String fingerprint);
    List<PickupRider> findTop50ByActiveTrueAndNextSyncAtLessThanEqualOrderByNextSyncAtAsc(Instant now);
}
