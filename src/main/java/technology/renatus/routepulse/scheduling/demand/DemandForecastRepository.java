package technology.renatus.routepulse.scheduling.demand;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DemandForecastRepository extends JpaRepository<DemandForecastWindow, Long> {
    @Query("SELECT w FROM DemandForecastWindow w JOIN FETCH w.sourceImport WHERE w.organization.id = :org AND w.eventDate = :date ORDER BY w.windowStart")
    List<DemandForecastWindow> forDate(@Param("org") Long organizationId, @Param("date") LocalDate date);

    @Query("SELECT DISTINCT w.eventDate FROM DemandForecastWindow w WHERE w.organization.id = :org ORDER BY w.eventDate DESC")
    List<LocalDate> availableDates(@Param("org") Long organizationId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DemandForecastWindow w WHERE w.organization.id = :org AND w.sourceDate IN :dates")
    int replaceDates(@Param("org") Long organizationId, @Param("dates") List<LocalDate> dates);

    @Modifying
    @Query("DELETE FROM DemandForecastWindow w WHERE w.sourceDate < :cutoff")
    int purgeBefore(@Param("cutoff") LocalDate cutoff);
}
