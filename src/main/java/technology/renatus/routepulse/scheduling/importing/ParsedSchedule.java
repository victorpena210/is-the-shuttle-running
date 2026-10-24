package technology.renatus.routepulse.scheduling.importing;
import java.time.LocalDate;
import java.util.*;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** Weekly grids carry every reported driver/date, including explicit days off. */
public record ParsedSchedule(List<ShiftRecord> shifts, Map<String, Set<LocalDate>> driverDates) {
    public boolean weeklyDriverReport() { return !driverDates.isEmpty(); }
}
