package technology.renatus.routepulse.attention.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.attention.model.AttentionCenter;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.service.checks.CoverageAttentionChecker;
import technology.renatus.routepulse.attention.service.checks.LiveOperationsAttentionChecker;
import technology.renatus.routepulse.attention.service.checks.ScheduleAttentionChecker;
import technology.renatus.routepulse.attention.service.checks.ServiceVerificationAttentionChecker;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;

/** Coordinates the independent Needs Attention checks for the manager dashboard. */
@Service
public class AttentionCenterService {

    private final ZoneId operationsZone;
    private final ScheduleAttentionChecker scheduleAttentionChecker;
    private final CoverageAttentionChecker coverageAttentionChecker;
    private final LiveOperationsAttentionChecker liveOperationsAttentionChecker;
    private final ServiceVerificationAttentionChecker serviceVerificationAttentionChecker;
    private final technology.renatus.routepulse.driving.service.RiderStatusService riderStatus;
    private final technology.renatus.routepulse.organization.repository.OrganizationRepository organizations;

    private final technology.renatus.routepulse.driving.repository.DrivingSessionRepository drivingSessions;

    public AttentionCenterService(
            ScheduleAttentionChecker scheduleAttentionChecker,
            CoverageAttentionChecker coverageAttentionChecker,
            LiveOperationsAttentionChecker liveOperationsAttentionChecker,
            ServiceVerificationAttentionChecker serviceVerificationAttentionChecker,
            technology.renatus.routepulse.driving.service.RiderStatusService riderStatus,
            technology.renatus.routepulse.organization.repository.OrganizationRepository organizations,
            technology.renatus.routepulse.driving.repository.DrivingSessionRepository drivingSessions,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.drivingSessions = drivingSessions;

        this.scheduleAttentionChecker = scheduleAttentionChecker;
        this.coverageAttentionChecker = coverageAttentionChecker;
        this.liveOperationsAttentionChecker = liveOperationsAttentionChecker;
        this.serviceVerificationAttentionChecker = serviceVerificationAttentionChecker;
        this.riderStatus = riderStatus;
        this.organizations = organizations;
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public AttentionCenter build(Long organizationId, DashboardPlan plan) {
        if (organizationId == null) {
            throw new IllegalArgumentException("Organization id is required.");
        }
        if (plan == null) {
            throw new IllegalArgumentException("Dashboard plan is required.");
        }

        List<AttentionItem> items = new ArrayList<>();
        organizations.findById(organizationId).ifPresent(org -> {
            var status = riderStatus.getStatus(org.getSlug());
            if (status.operationalStatus() == technology.renatus.routepulse.operations.domain.RiderOperationalStatus.CONFIRMATION_NEEDED) {
                items.add(new AttentionItem("service-confirmation-overdue",
                        technology.renatus.routepulse.attention.model.AttentionSeverity.URGENT,
                        "Rider status needs confirmation", status.detail(), "Resolve live status", "/app#live-status"));
            }
        });

        long timeouts = drivingSessions.countByOrganization_IdAndEndReasonAndInactivityReviewedAtIsNull(organizationId,
                technology.renatus.routepulse.driving.domain.DrivingSessionEndReason.INACTIVITY_TIMEOUT);
        if (timeouts > 0) items.add(new AttentionItem("inactivity-timeout-review",
                technology.renatus.routepulse.attention.model.AttentionSeverity.WARNING,
                "Inactivity timeout — needs review", timeouts + " automatically closed session(s) need a manager review.",
                "Review timeouts", "/operations/movement"));

        scheduleAttentionChecker.addAlerts(plan, items);
        coverageAttentionChecker.addAlerts(plan, items);
        LocalDateTime now = LocalDateTime.now(operationsZone);
        liveOperationsAttentionChecker.addAlerts(
                plan,
                now,
                items
        );
        serviceVerificationAttentionChecker.addAlerts(
                organizationId,
                plan,
                now,
                items
        );

        List<AttentionItem> sortedItems = items.stream()
                .sorted(Comparator.comparingInt(
                        item -> item.severity().getPriority()
                ))
                .toList();

        return new AttentionCenter(sortedItems);
    }
}
