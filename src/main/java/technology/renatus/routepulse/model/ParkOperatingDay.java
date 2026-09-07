package technology.renatus.routepulse.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record ParkOperatingDay(
        LocalDate date,
        ParkOperatingStatus status,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean buyout,
        String verificationSource,
        String diagnosticCode
) {

    public boolean shouldScheduleShuttle() {
        return status == ParkOperatingStatus.OPEN;
    }

    public boolean isPersistedSnapshot() {
        return "PERSISTED_SNAPSHOT".equals(verificationSource);
    }

    public boolean isManagerConfirmed() {
        return "MANAGER_CONFIRMATION".equals(verificationSource);
    }

    public boolean isVerified() {
        return status != ParkOperatingStatus.UNKNOWN;
    }
}
