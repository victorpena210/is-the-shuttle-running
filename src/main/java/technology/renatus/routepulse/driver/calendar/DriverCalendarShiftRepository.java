package technology.renatus.routepulse.driver.calendar;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface DriverCalendarShiftRepository extends JpaRepository<DriverCalendarShift,Long> {
    List<DriverCalendarShift> findByConnection_IdOrderByStartsAt(Long id);
    @Modifying(flushAutomatically=true)
    @Query("delete from DriverCalendarShift s where s.connection.id = :id")
    void clear(@Param("id") Long id);
    @Query("select s from DriverCalendarShift s join fetch s.connection c where c.organizationId = :org and s.startsAt < :end and s.endsAt > :start")
    List<DriverCalendarShift> overlapping(@Param("org") Long org,@Param("start") Instant start,@Param("end") Instant end);
}
