package technology.renatus.routepulse.driver.calendar;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriverCalendarConnectionRepository extends JpaRepository<DriverCalendarConnection,Long> {
    Optional<DriverCalendarConnection> findByDriver_IdAndOrganizationId(Long driver,Long org);
    boolean existsByOrganizationIdAndFeedFingerprintAndDriver_IdNot(Long org,String fingerprint,Long driver);
    List<DriverCalendarConnection> findAllByOrganizationId(Long org);
    List<DriverCalendarConnection> findTop50ByNextSyncAtLessThanEqualOrderByNextSyncAtAsc(Instant now);
}
