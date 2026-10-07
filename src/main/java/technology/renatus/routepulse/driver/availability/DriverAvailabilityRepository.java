package technology.renatus.routepulse.driver.availability;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailabilityRevision, Long> {
    @Query("""
        select r from DriverAvailabilityRevision r join fetch r.driver d
        where r.organizationId = :org and r.shiftDate between :from and :to
        and r.id = (select max(v.id) from DriverAvailabilityRevision v
            where v.organizationId = :org and v.driver.id = d.id and v.shiftDate = r.shiftDate)
        order by r.shiftDate, d.name
        """)
    List<DriverAvailabilityRevision> latest(@Param("org") Long org,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
    @Query("select coalesce(max(r.id), 0) from DriverAvailabilityRevision r where r.organizationId = :org and r.driver.id = :driver")
    long version(@Param("org") Long org, @Param("driver") Long driver);
    List<DriverAvailabilityRevision> findTop100ByOrganizationIdAndDriver_IdOrderByIdDesc(Long org, Long driver);
    Optional<DriverAvailabilityRevision> findByIdAndOrganizationId(Long id, Long org);
}
