package technology.renatus.routepulse.calendar.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.calendar.model.ParkCalendarOverride;

public interface ParkCalendarOverrideRepository
        extends JpaRepository<ParkCalendarOverride, Long> {

    Optional<ParkCalendarOverride>
            findFirstByOrganizationIdAndOperatingDateOrderByConfirmedAtDescIdDesc(
                    Long organizationId,
                    LocalDate operatingDate);
}
