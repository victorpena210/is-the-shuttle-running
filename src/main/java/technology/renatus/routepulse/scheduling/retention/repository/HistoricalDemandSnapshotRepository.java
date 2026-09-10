package technology.renatus.routepulse.scheduling.retention.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.scheduling.retention.model.HistoricalDemandSnapshot;

public interface HistoricalDemandSnapshotRepository
        extends JpaRepository<HistoricalDemandSnapshot, Long> {

    Optional<HistoricalDemandSnapshot>
            findByOrganization_IdAndOperatingDate(
                    Long organizationId,
                    LocalDate operatingDate
            );

    List<HistoricalDemandSnapshot>
            findAllByOrganization_IdAndOperatingDateBetweenOrderByOperatingDateAsc(
                    Long organizationId,
                    LocalDate startDate,
                    LocalDate endDate
            );

    long deleteByOperatingDateBefore(LocalDate cutoffDate);
}
