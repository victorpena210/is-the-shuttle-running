package technology.renatus.routepulse.calendar.service;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.spi.OperatingCalendarProvider;

/** Application entry point for the configured operating-calendar provider. */
@Service
public class ParkOperatingDayService {

    private final OperatingCalendarProvider calendarProvider;

    public ParkOperatingDayService(
            OperatingCalendarProvider calendarProvider) {
        this.calendarProvider = calendarProvider;
    }

    public ParkOperatingDay getOperatingDay(LocalDate date) {
        return calendarProvider.getOperatingDay(date);
    }

    public void confirmOpenForDate(LocalDate date, String confirmedBy) {
        calendarProvider.confirmOpenForDate(date, confirmedBy);
    }
}
