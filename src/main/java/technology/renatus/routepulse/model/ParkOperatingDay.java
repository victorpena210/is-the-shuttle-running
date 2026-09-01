package technology.renatus.routepulse.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record ParkOperatingDay(
        LocalDate date,
        ParkOperatingStatus status,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean buyout
) {

    public boolean shouldScheduleShuttle() {
        return status == ParkOperatingStatus.OPEN;
    }
}