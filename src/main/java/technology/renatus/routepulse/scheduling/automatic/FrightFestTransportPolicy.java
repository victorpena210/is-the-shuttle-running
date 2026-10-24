package technology.renatus.routepulse.scheduling.automatic;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;

/** Explicit, non-recurring remainder of the 2026 event season. */
@Component
public class FrightFestTransportPolicy {
    private final LocalDate start;
    private final LocalDate end;
    public FrightFestTransportPolicy(
            @Value("${app.automatic-schedule.fright-fest-start:2026-10-24}") String start,
            @Value("${app.automatic-schedule.fright-fest-end:2026-11-01}") String end) {
        this.start = LocalDate.parse(start);
        this.end = LocalDate.parse(end);
        if (this.end.isBefore(this.start)) throw new IllegalArgumentException("Fright Fest end precedes its start.");
    }
    public boolean required(ParkOperatingDay day) {
        return day.status() == ParkOperatingStatus.OPEN
                && !day.date().isBefore(start) && !day.date().isAfter(end);
    }
}
