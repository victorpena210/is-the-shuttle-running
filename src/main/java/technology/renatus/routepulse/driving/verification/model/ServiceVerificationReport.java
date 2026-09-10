package technology.renatus.routepulse.driving.verification.model;

import java.time.LocalDate;
import java.util.List;

/** Manager-facing summary of service evidence for one operating date. */
public record ServiceVerificationReport(
        LocalDate operatingDate,
        List<ServiceVerificationPeriod> periods,
        int expectedDemand,
        long passengerConfirmations,
        long activeDrivingMinutes,
        int reviewCount) {

    public ServiceVerificationReport {
        periods = List.copyOf(periods);
    }

    public static ServiceVerificationReport empty(LocalDate operatingDate) {
        return new ServiceVerificationReport(
                operatingDate,
                List.of(),
                0,
                0,
                0,
                0
        );
    }

    public boolean hasDemand() {
        return expectedDemand > 0 && !periods.isEmpty();
    }

    public boolean hasEvidence() {
        return passengerConfirmations > 0 || activeDrivingMinutes > 0;
    }

    public boolean needsReview() {
        return reviewCount > 0;
    }
}
