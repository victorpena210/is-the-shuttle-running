package technology.renatus.routepulse.driving.verification.model;

import java.util.List;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;

/** Aggregated service verification for one RoutePulse shuttle period. */
public record ServiceVerificationPeriod(
        ShuttlePeriod period,
        String title,
        Long assignmentId,
        String scheduledDriverName,
        String actualDriverNames,
        int expectedDemand,
        long passengerConfirmations,
        long activeDrivingMinutes,
        int evaluatedWindowCount,
        int noEvidenceWindowCount,
        int longestZeroConfirmationStreak,
        boolean weakPassengerEvidence,
        boolean needsReview,
        ServiceVerificationConfidence confidence,
        String reviewMessage,
        List<ServiceVerificationWindow> windows) {

    public ServiceVerificationPeriod {
        windows = List.copyOf(windows);
    }

    public boolean hasAssignment() {
        return assignmentId != null;
    }

    public boolean hasEvidence() {
        return passengerConfirmations > 0 || activeDrivingMinutes > 0;
    }
}
