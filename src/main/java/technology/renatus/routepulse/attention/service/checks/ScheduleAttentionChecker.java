package technology.renatus.routepulse.attention.service.checks;

import java.util.List;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.attention.model.AttentionItem;
import technology.renatus.routepulse.attention.model.AttentionSeverity;
import technology.renatus.routepulse.attention.service.AttentionSupport;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;

/** Flags schedule availability and operating-calendar problems. */
@Component
public class ScheduleAttentionChecker {

    public void addAlerts(
            DashboardPlan plan,
            List<AttentionItem> items) {

        if (!plan.scheduleUploaded()) {
            items.add(new AttentionItem(
                    "schedule-not-uploaded",
                    AttentionSeverity.WARNING,
                    "No employee schedule is uploaded",
                    "Upload the current Dayforce schedule before RoutePulse can calculate demand or driver coverage.",
                    "Upload schedule",
                    "#schedules"
            ));
            return;
        }

        if (!plan.selectedDateAvailable()) {
            items.add(new AttentionItem(
                    "date-not-in-schedule",
                    AttentionSeverity.WARNING,
                    "This date is not in the uploaded schedule",
                    "Choose a planning date included in the latest schedule import before reviewing transportation demand.",
                    "Choose planning date",
                    AttentionSupport.dashboardHref(plan, "demand")
            ));
            return;
        }

        if (plan.operatingDay() != null
                && plan.operatingDay().status() == ParkOperatingStatus.UNKNOWN
                && !plan.calendarOverrideActive()) {

            items.add(new AttentionItem(
                    "calendar-unverified",
                    AttentionSeverity.WARNING,
                    "Park calendar could not be verified",
                    "Transportation demand is available, but RoutePulse is holding automatic driver coverage until a manager confirms the park is open.",
                    "Review calendar",
                    AttentionSupport.dashboardHref(plan, "demand")
            ));
        }
    }
}
