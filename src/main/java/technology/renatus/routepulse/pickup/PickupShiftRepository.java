package technology.renatus.routepulse.pickup;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface PickupShiftRepository extends JpaRepository<PickupShift,Long> {
    void deleteByRider_Id(Long id);
    @Query("select s from PickupShift s join fetch s.rider r where r.organizationId=:org and r.active=true and s.endsAt >= :start and s.endsAt < :end order by s.startsAt, s.endsAt")
    List<PickupShift> endingBetween(@Param("org") Long org,@Param("start") Instant start,@Param("end") Instant end);
}
