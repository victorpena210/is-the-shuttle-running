package technology.renatus.routepulse.scheduling.automatic;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomaticScheduleRepository extends JpaRepository<AutomaticScheduleDay,Long> {
    Optional<AutomaticScheduleDay> findByOrganizationIdAndDate(Long org,LocalDate date);
    List<AutomaticScheduleDay> findAllByOrganizationIdAndDateBetweenOrderByDate(Long org,LocalDate from,LocalDate to);
}
