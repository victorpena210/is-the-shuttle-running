package technology.renatus.routepulse.attention.service.checks;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.attention.service.AttentionSupport;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.reporting.history.ServiceHistoryService;
import technology.renatus.routepulse.reporting.history.ServiceReviewFilters;

/** Uses the same service evidence and unscheduled activity as the Service review page. */
@Component
public class ServiceVerificationAttentionChecker {
    private final ServiceHistoryService history;
    public ServiceVerificationAttentionChecker(ServiceHistoryService history) { this.history = history; }

    public void addAlerts(Long organizationId, DashboardPlan plan, LocalDateTime now, List<AttentionItem> items) {
        if (!AttentionSupport.shouldPlanCoverage(plan) || plan.selectedDate() == null
                || plan.selectedDate().isAfter(now.toLocalDate())) return;
        var date = plan.selectedDate();
        var filters = ServiceReviewFilters.resolve(date, date, null, null, null, null, null, now.toLocalDate());
        for (var row : history.build(organizationId, filters).rows()) {
            if (!row.needsReview()) continue;
            // Existing live alerts already explain a currently late or missing start.
            String period = "evening".equals(row.period()) ? "AFTERNOON" : row.period().toUpperCase(java.util.Locale.ROOT);
            boolean liveAlert = plan.driverAssignments().stream().filter(a -> a.getPeriod().name().equals(period))
                    .anyMatch(a -> items.stream().anyMatch(item -> item.key().equals("driving-not-started-" + a.getId())
                            || item.key().startsWith("driving-window-not-active-" + a.getId() + "-")));
            if (liveAlert) continue;
            items.add(new AttentionItem("service-review-gap-" + row.period(), AttentionSeverity.WARNING,
                    "Review " + row.title().toLowerCase(java.util.Locale.ROOT) + " service evidence",
                    row.gaps() + " completed demand window(s) have no recorded driving or rider evidence. "
                            + "Scheduled and unscheduled driving were included. Review the records before drawing a conclusion.",
                    "Open service review", "/service-history?from=" + date + "&to=" + date + "&period=" + row.period()));
        }
    }
}
