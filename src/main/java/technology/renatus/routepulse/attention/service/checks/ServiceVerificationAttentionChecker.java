package technology.renatus.routepulse.attention.service.checks;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.attention.service.AttentionSupport;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationPeriod;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationReport;
import technology.renatus.routepulse.driving.verification.service.ServiceVerificationService;

/** Adds conservative cross-signal service verification concerns to Needs Attention. */
@Component
public class ServiceVerificationAttentionChecker {

    private final ServiceVerificationService verificationService;
    private final ZoneId operationsZone;

    public ServiceVerificationAttentionChecker(
            ServiceVerificationService verificationService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.verificationService = verificationService;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public void addAlerts(
            Long organizationId,
            DashboardPlan plan,
            LocalDateTime now,
            List<AttentionItem> items) {

        if (!AttentionSupport.shouldPlanCoverage(plan)
                || plan.selectedDate() == null
                || plan.transportationDemand() == null) {
            return;
        }

        ServiceVerificationReport report = verificationService.build(
                organizationId,
                plan.selectedDate(),
                plan.transportationDemand(),
                plan.driverAssignments(),
                now.atZone(operationsZone).toInstant()
        );

        for (ServiceVerificationPeriod period : report.periods()) {
            if (!period.needsReview()
                    || !period.hasAssignment()
                    || hasHigherPriorityLiveAlert(period, items)) {
                continue;
            }

            if (period.noEvidenceWindowCount() > 0) {
                addNoEvidenceAlert(plan, period, items);
            } else if (period.weakPassengerEvidence()) {
                addWeakPassengerEvidenceAlert(plan, period, items);
            }
        }
    }

    private void addNoEvidenceAlert(
            DashboardPlan plan,
            ServiceVerificationPeriod period,
            List<AttentionItem> items) {

        String windowLabel = period.noEvidenceWindowCount() == 1
                ? "one completed 15-minute demand window"
                : period.noEvidenceWindowCount()
                + " completed 15-minute demand windows";

        items.add(new AttentionItem(
                "service-verification-gap-" + period.period().name().toLowerCase(),
                AttentionSeverity.WARNING,
                "Possible " + period.title().toLowerCase() + " service gap",
                "RoutePulse found " + windowLabel
                        + " with meaningful scheduled demand but no recorded driving-session "
                        + "or passenger QR evidence. This does not prove the shuttle did not run; "
                        + "review the service evidence.",
                "Review service evidence",
                AttentionSupport.dashboardHref(plan, "demand")
        ));
    }

    private void addWeakPassengerEvidenceAlert(
            DashboardPlan plan,
            ServiceVerificationPeriod period,
            List<AttentionItem> items) {

        int minutes = period.longestZeroConfirmationStreak() * 15;

        items.add(new AttentionItem(
                "service-verification-weak-qr-"
                        + period.period().name().toLowerCase(),
                AttentionSeverity.INFO,
                period.title() + " has limited rider confirmation evidence",
                "Driving sessions were recorded, but no passenger QR confirmations were received "
                        + "across about " + minutes
                        + " minutes of meaningful demand. Rider scans are optional, so this is a "
                        + "review signal rather than a finding that service was missed.",
                "Review service evidence",
                AttentionSupport.dashboardHref(plan, "demand")
        ));
    }

    private boolean hasHigherPriorityLiveAlert(
            ServiceVerificationPeriod period,
            List<AttentionItem> items) {

        if (period.assignmentId() == null) {
            return false;
        }

        String assignmentId = String.valueOf(period.assignmentId());
        return items.stream()
                .map(AttentionItem::key)
                .anyMatch(key -> key.equals("driving-not-started-" + assignmentId)
                        || key.startsWith("driving-window-not-active-" + assignmentId + "-")
                        || key.equals("driving-past-end-" + assignmentId));
    }
}
