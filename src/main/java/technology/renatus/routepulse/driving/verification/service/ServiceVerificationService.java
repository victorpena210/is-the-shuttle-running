package technology.renatus.routepulse.driving.verification.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationConfidence;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationPeriod;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationReport;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationWindow;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

/**
 * Cross-checks anonymous schedule demand, actual driving sessions and rider QR
 * confirmations. The result is evidence of service activity, not a misconduct
 * determination and not an estimate of how many people rode the shuttle.
 */
@Service
public class ServiceVerificationService {

    private final PassengerRideConfirmationRepository confirmationRepository;
    private final ZoneId operationsZone;
    private final int minimumDemandForReview;
    private final int zeroConfirmationWindowsForReview;
    private final int strongDrivingCoveragePercent;

    public ServiceVerificationService(
            PassengerRideConfirmationRepository confirmationRepository,
            @Value("${app.service-verification.minimum-demand-for-review:10}")
            int minimumDemandForReview,
            @Value("${app.service-verification.zero-confirmation-windows-for-review:2}")
            int zeroConfirmationWindowsForReview,
            @Value("${app.service-verification.strong-driving-coverage-percent:50}")
            int strongDrivingCoveragePercent,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {

        if (minimumDemandForReview < 1) {
            throw new IllegalArgumentException(
                    "Service verification minimum demand must be at least 1."
            );
        }
        if (zeroConfirmationWindowsForReview < 1) {
            throw new IllegalArgumentException(
                    "Zero-confirmation review windows must be at least 1."
            );
        }
        if (strongDrivingCoveragePercent < 1 || strongDrivingCoveragePercent > 100) {
            throw new IllegalArgumentException(
                    "Strong driving coverage percent must be between 1 and 100."
            );
        }

        this.confirmationRepository = confirmationRepository;
        this.minimumDemandForReview = minimumDemandForReview;
        this.zeroConfirmationWindowsForReview = zeroConfirmationWindowsForReview;
        this.strongDrivingCoveragePercent = strongDrivingCoveragePercent;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    @Transactional(readOnly = true)
    public ServiceVerificationReport build(
            Long organizationId,
            LocalDate operatingDate,
            TransportationDemandAnalysis demand,
            List<DriverAssignment> assignments) {

        return build(
                organizationId,
                operatingDate,
                demand,
                assignments,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public ServiceVerificationReport build(
            Long organizationId,
            LocalDate operatingDate,
            TransportationDemandAnalysis demand,
            List<DriverAssignment> assignments,
            Instant now) {

        if (organizationId == null) {
            throw new IllegalArgumentException("Organization id is required.");
        }
        if (operatingDate == null || demand == null || !demand.hasAnyDemand()) {
            return ServiceVerificationReport.empty(operatingDate);
        }

        Instant dayStart = operatingDate.atStartOfDay(operationsZone).toInstant();
        Instant dayEnd = operatingDate.plusDays(1).atStartOfDay(operationsZone).toInstant();

        List<PassengerRideConfirmation> confirmations = confirmationRepository
                .findAllForServiceVerification(
                        organizationId,
                        dayStart,
                        dayEnd
                );

        Map<ShuttlePeriod, DriverAssignment> assignmentByPeriod = new EnumMap<>(ShuttlePeriod.class);
        if (assignments != null) {
            assignments.forEach(assignment -> assignmentByPeriod.put(
                    assignment.getPeriod(),
                    assignment
            ));
        }

        List<ServiceVerificationPeriod> periods = new ArrayList<>();
        addPeriod(
                periods,
                ShuttlePeriod.MORNING,
                demand.getMorning(),
                assignmentByPeriod.get(ShuttlePeriod.MORNING),
                confirmations,
                operatingDate,
                now
        );
        addPeriod(
                periods,
                ShuttlePeriod.MIDDAY,
                demand.getMidday(),
                assignmentByPeriod.get(ShuttlePeriod.MIDDAY),
                confirmations,
                operatingDate,
                now
        );
        addPeriod(
                periods,
                ShuttlePeriod.AFTERNOON,
                demand.getEvening(),
                assignmentByPeriod.get(ShuttlePeriod.AFTERNOON),
                confirmations,
                operatingDate,
                now
        );

        int expectedDemand = periods.stream()
                .mapToInt(ServiceVerificationPeriod::expectedDemand)
                .sum();
        long passengerConfirmations = periods.stream()
                .mapToLong(ServiceVerificationPeriod::passengerConfirmations)
                .sum();
        long activeDrivingMinutes = periods.stream()
                .mapToLong(ServiceVerificationPeriod::activeDrivingMinutes)
                .sum();
        int reviewCount = (int) periods.stream()
                .filter(ServiceVerificationPeriod::needsReview)
                .count();

        return new ServiceVerificationReport(
                operatingDate,
                periods,
                expectedDemand,
                passengerConfirmations,
                activeDrivingMinutes,
                reviewCount
        );
    }

    private void addPeriod(
            List<ServiceVerificationPeriod> periods,
            ShuttlePeriod shuttlePeriod,
            TransportationDemandPeriod demandPeriod,
            DriverAssignment assignment,
            List<PassengerRideConfirmation> confirmations,
            LocalDate operatingDate,
            Instant now) {

        if (demandPeriod == null || !demandPeriod.hasDemand()) {
            return;
        }

        List<ServiceVerificationWindow> windows = demandPeriod.getWindows().stream()
                .filter(window -> window.getDemandCount() > 0)
                .map(window -> verifyWindow(
                        window,
                        assignment,
                        confirmations,
                        operatingDate,
                        now
                ))
                .toList();

        int evaluatedWindowCount = (int) windows.stream()
                .filter(ServiceVerificationWindow::completed)
                .filter(ServiceVerificationWindow::meaningfulDemand)
                .count();
        int noEvidenceWindowCount = (int) windows.stream()
                .filter(ServiceVerificationWindow::isReviewCandidate)
                .count();
        int zeroConfirmationStreak = longestZeroConfirmationStreak(windows);
        boolean weakPassengerEvidence = zeroConfirmationStreak >= zeroConfirmationWindowsForReview
                && windows.stream().anyMatch(ServiceVerificationWindow::hasDrivingEvidence);
        boolean outcomeAlreadyReviewed = assignment != null
                && isReviewedNonServiceOutcome(assignment.getAssignmentOutcome());
        boolean needsReview = !outcomeAlreadyReviewed
                && (noEvidenceWindowCount > 0 || weakPassengerEvidence);

        ServiceVerificationConfidence confidence = aggregateConfidence(
                windows,
                evaluatedWindowCount
        );
        if (weakPassengerEvidence && confidence == ServiceVerificationConfidence.HIGH) {
            confidence = ServiceVerificationConfidence.MEDIUM;
        }

        String reviewMessage = outcomeAlreadyReviewed
                ? null
                : buildReviewMessage(
                        noEvidenceWindowCount,
                        zeroConfirmationStreak,
                        weakPassengerEvidence
                );

        long periodConfirmations = windows.stream()
                .mapToLong(ServiceVerificationWindow::passengerConfirmations)
                .sum();
        long periodDrivingMinutes = windows.stream()
                .mapToLong(ServiceVerificationWindow::activeDrivingMinutes)
                .sum();

        periods.add(new ServiceVerificationPeriod(
                shuttlePeriod,
                demandPeriod.getSummaryTitle(),
                assignment == null ? null : assignment.getId(),
                assignment == null || assignment.getScheduledDriver() == null
                        ? null
                        : assignment.getScheduledDriver().getName(),
                assignment == null ? null : normalizeText(assignment.getActualDriverNames()),
                demandPeriod.getTotalCount(),
                periodConfirmations,
                periodDrivingMinutes,
                evaluatedWindowCount,
                noEvidenceWindowCount,
                zeroConfirmationStreak,
                weakPassengerEvidence,
                needsReview,
                confidence,
                reviewMessage,
                windows
        ));
    }

    private ServiceVerificationWindow verifyWindow(
            TransportationDemandWindow demandWindow,
            DriverAssignment assignment,
            List<PassengerRideConfirmation> confirmations,
            LocalDate operatingDate,
            Instant now) {

        Instant windowStart = operatingDate
                .atTime(demandWindow.getWindowStart())
                .atZone(operationsZone)
                .toInstant();
        Instant windowEnd = operatingDate
                .atTime(demandWindow.getWindowEnd())
                .atZone(operationsZone)
                .toInstant();

        boolean completed = !windowEnd.isAfter(now);
        boolean meaningfulDemand = demandWindow.getDemandCount() >= minimumDemandForReview;

        long passengerConfirmations = countConfirmations(
                confirmations,
                assignment,
                windowStart,
                windowEnd
        );
        long activeDrivingMinutes = calculateActiveDrivingMinutes(
                assignment,
                windowStart,
                windowEnd,
                now
        );

        long windowMinutes = Math.max(1, Duration.between(windowStart, windowEnd).toMinutes());
        int drivingCoveragePercent = (int) Math.min(
                100,
                Math.round((activeDrivingMinutes * 100.0) / windowMinutes)
        );

        boolean noServiceEvidence = completed
                && meaningfulDemand
                && activeDrivingMinutes == 0
                && passengerConfirmations == 0;

        ServiceVerificationConfidence confidence = classifyWindow(
                completed,
                meaningfulDemand,
                passengerConfirmations,
                drivingCoveragePercent,
                noServiceEvidence
        );

        return new ServiceVerificationWindow(
                demandWindow.getWindowStart(),
                demandWindow.getWindowEnd(),
                demandWindow.getDemandCount(),
                passengerConfirmations,
                activeDrivingMinutes,
                drivingCoveragePercent,
                completed,
                meaningfulDemand,
                noServiceEvidence,
                confidence
        );
    }

    private long countConfirmations(
            List<PassengerRideConfirmation> confirmations,
            DriverAssignment assignment,
            Instant start,
            Instant end) {

        if (assignment == null || assignment.getId() == null) {
            return 0;
        }

        return confirmations.stream()
                .filter(confirmation -> confirmation.getAssignment() != null)
                .filter(confirmation -> assignment.getId().equals(
                        confirmation.getAssignment().getId()
                ))
                .filter(confirmation -> !confirmation.getSubmittedAt().isBefore(start))
                .filter(confirmation -> confirmation.getSubmittedAt().isBefore(end))
                .count();
    }

    private long calculateActiveDrivingMinutes(
            DriverAssignment assignment,
            Instant windowStart,
            Instant windowEnd,
            Instant now) {

        if (assignment == null || assignment.getDrivingSessions().isEmpty()) {
            return 0;
        }

        long seconds = 0;
        for (DrivingSession session : assignment.getDrivingSessions()) {
            Instant sessionStart = session.getStartedAt();
            Instant sessionEnd = session.getEndedAt() == null
                    ? now
                    : session.getEndedAt();

            Instant overlapStart = sessionStart.isAfter(windowStart)
                    ? sessionStart
                    : windowStart;
            Instant overlapEnd = sessionEnd.isBefore(windowEnd)
                    ? sessionEnd
                    : windowEnd;

            if (overlapEnd.isAfter(overlapStart)) {
                seconds += Duration.between(overlapStart, overlapEnd).getSeconds();
            }
        }

        long maxWindowSeconds = Math.max(0, Duration.between(windowStart, windowEnd).getSeconds());
        return Math.min(maxWindowSeconds, seconds) / 60;
    }

    private ServiceVerificationConfidence classifyWindow(
            boolean completed,
            boolean meaningfulDemand,
            long passengerConfirmations,
            int drivingCoveragePercent,
            boolean noServiceEvidence) {

        if (!completed) {
            return ServiceVerificationConfidence.UPCOMING;
        }
        if (!meaningfulDemand) {
            return ServiceVerificationConfidence.INSUFFICIENT;
        }
        if (noServiceEvidence) {
            return ServiceVerificationConfidence.LOW;
        }
        if (passengerConfirmations > 0
                && drivingCoveragePercent >= strongDrivingCoveragePercent) {
            return ServiceVerificationConfidence.HIGH;
        }
        return ServiceVerificationConfidence.MEDIUM;
    }

    private ServiceVerificationConfidence aggregateConfidence(
            List<ServiceVerificationWindow> windows,
            int evaluatedWindowCount) {

        if (evaluatedWindowCount == 0) {
            return windows.stream().allMatch(window -> !window.completed())
                    ? ServiceVerificationConfidence.UPCOMING
                    : ServiceVerificationConfidence.INSUFFICIENT;
        }
        if (windows.stream().anyMatch(window ->
                window.confidence() == ServiceVerificationConfidence.LOW)) {
            return ServiceVerificationConfidence.LOW;
        }
        boolean hasHigh = windows.stream().anyMatch(window ->
                window.confidence() == ServiceVerificationConfidence.HIGH);
        return hasHigh
                ? ServiceVerificationConfidence.HIGH
                : ServiceVerificationConfidence.MEDIUM;
    }

    private int longestZeroConfirmationStreak(
            List<ServiceVerificationWindow> windows) {

        int longest = 0;
        int current = 0;

        for (ServiceVerificationWindow window : windows) {
            if (window.completed()
                    && window.meaningfulDemand()
                    && window.passengerConfirmations() == 0) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }

        return longest;
    }

    private String buildReviewMessage(
            int noEvidenceWindowCount,
            int zeroConfirmationStreak,
            boolean weakPassengerEvidence) {

        if (noEvidenceWindowCount > 0) {
            return noEvidenceWindowCount == 1
                    ? "One completed demand window has no driving-session or rider-confirmation evidence."
                    : noEvidenceWindowCount
                    + " completed demand windows have no driving-session or rider-confirmation evidence.";
        }
        if (weakPassengerEvidence) {
            return "Driving sessions were recorded, but no rider confirmations were received across "
                    + zeroConfirmationStreak
                    + " consecutive meaningful demand windows.";
        }
        return null;
    }

    private boolean isReviewedNonServiceOutcome(AssignmentOutcome outcome) {
        return outcome == AssignmentOutcome.MISSED
                || outcome == AssignmentOutcome.EXCUSED
                || outcome == AssignmentOutcome.CANCELLED;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() || "—".equals(value.trim())
                ? null
                : value;
    }
}
