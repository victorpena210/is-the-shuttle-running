package technology.renatus.routepulse.provider;

import java.time.LocalDate;
import java.util.List;

import technology.renatus.routepulse.model.ShiftRecord;

public interface ScheduleProvider {

    List<ShiftRecord> getSchedulesForDate(
            Long organizationId,
            LocalDate date
    );
}