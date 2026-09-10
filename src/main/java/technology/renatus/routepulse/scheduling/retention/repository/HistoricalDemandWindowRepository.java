package technology.renatus.routepulse.scheduling.retention.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.scheduling.retention.model.HistoricalDemandWindow;

public interface HistoricalDemandWindowRepository
        extends JpaRepository<HistoricalDemandWindow, Long> {

    List<HistoricalDemandWindow>
            findAllBySnapshot_Organization_IdAndSnapshot_OperatingDateOrderByWindowStartAsc(
                    Long organizationId,
                    LocalDate operatingDate
            );
}
