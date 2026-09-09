package technology.renatus.routepulse.calendar.repository;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.calendar.model.ParkOperatingDaySnapshot;

public interface ParkOperatingDaySnapshotRepository
        extends JpaRepository<ParkOperatingDaySnapshot, Long> {

    Optional<ParkOperatingDaySnapshot>
            findByParkIdAndOperatingDate(
                    int parkId,
                    LocalDate operatingDate
            );
}
