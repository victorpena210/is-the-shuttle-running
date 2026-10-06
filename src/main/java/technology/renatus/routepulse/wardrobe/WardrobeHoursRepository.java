package technology.renatus.routepulse.wardrobe;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardrobeHoursRepository extends JpaRepository<WardrobeHoursRule, Long> {
    List<WardrobeHoursRule> findAllByOrganizationIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByCreatedAtDescIdDesc(
            Long organizationId, LocalDate startsOn, LocalDate endsOn);
}
