package technology.renatus.routepulse.driving.verification.model;

import java.time.LocalTime;

/** Evidence for one completed or upcoming 15-minute transportation-demand window. */
public record ServiceVerificationWindow(
        LocalTime windowStart,
        LocalTime windowEnd,
        int expectedDemand,
        long passengerConfirmations,
        long activeDrivingMinutes,
        int drivingCoveragePercent,
        boolean completed,
        boolean meaningfulDemand,
        boolean noServiceEvidence,
        ServiceVerificationConfidence confidence) {

    public boolean hasPassengerEvidence() {
        return passengerConfirmations > 0;
    }

    public boolean hasDrivingEvidence() {
        return activeDrivingMinutes > 0;
    }

    public boolean isReviewCandidate() {
        return completed && meaningfulDemand && noServiceEvidence;
    }
}
