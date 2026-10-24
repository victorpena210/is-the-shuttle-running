package technology.renatus.routepulse.scheduling.demand;

import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DriverScheduleWindowRepository extends JpaRepository<DriverScheduleWindow, Long> {
    @Query("select w from DriverScheduleWindow w join fetch w.driver where w.organizationId = :org and w.startsAt < :end and w.endsAt > :start order by w.startsAt")
    List<DriverScheduleWindow> overlapping(@Param("org") Long org, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    @Modifying(flushAutomatically = true)
    @Query("delete from DriverScheduleWindow w where w.organizationId = :org and w.sourceDate in :dates")
    void replaceDates(@Param("org") Long org, @Param("dates") Collection<LocalDate> dates);
    @Modifying(flushAutomatically = true)
    @Query("delete from DriverScheduleWindow w where w.organizationId = :org and w.driver.id = :driver")
    void clearDriver(@Param("org") Long org, @Param("driver") Long driver);
    @Modifying(flushAutomatically = true)
    @Query("delete from DriverScheduleWindow w where w.sourceDate < :before")
    void purgeBefore(@Param("before") LocalDate before);
}
