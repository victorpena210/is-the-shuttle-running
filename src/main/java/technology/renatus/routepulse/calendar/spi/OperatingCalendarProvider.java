package technology.renatus.routepulse.calendar.spi;

import java.time.LocalDate;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;

/** Calendar source for the configured park; implementations own verification and fallback. */
public interface OperatingCalendarProvider {
    ParkOperatingDay getOperatingDay(LocalDate date);
}
