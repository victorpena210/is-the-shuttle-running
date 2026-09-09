package technology.renatus.routepulse.scheduling.importing;

import java.time.LocalDate;
import java.util.List;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

public interface ScheduleProvider {

    List<ShiftRecord> getSchedulesForDate(
            Long organizationId,
            LocalDate date
    );
}
