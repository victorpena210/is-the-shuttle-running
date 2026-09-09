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
import technology.renatus.routepulse.dashboard.model.DashboardPlan;

/** Coordinates the independent Needs Attention checks for the manager dashboard. */
@Service
public class AttentionCenterService {

    private final ZoneId operationsZone;
    private final ScheduleAttentionChecker scheduleAttentionChecker;
    private final CoverageAttentionChecker coverageAttentionChecker;
    private final LiveOperationsAttentionChecker liveOperationsAttentionChecker;

    public AttentionCenterService(
            ScheduleAttentionChecker scheduleAttentionChecker,
            CoverageAttentionChecker coverageAttentionChecker,
            LiveOperationsAttentionChecker liveOperationsAttentionChecker,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {

        this.scheduleAttentionChecker = scheduleAttentionChecker;
        this.coverageAttentionChecker = coverageAttentionChecker;
        this.liveOperationsAttentionChecker = liveOperationsAttentionChecker;
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

        scheduleAttentionChecker.addAlerts(plan, items);
        coverageAttentionChecker.addAlerts(plan, items);
        liveOperationsAttentionChecker.addAlerts(
                plan,
                LocalDateTime.now(operationsZone),
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
