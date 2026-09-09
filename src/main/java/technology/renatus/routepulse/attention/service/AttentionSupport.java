package technology.renatus.routepulse.attention.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;

/** Shared calculations used by the independent attention checkers. */
public final class AttentionSupport {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private AttentionSupport() {
    }

    public static boolean shouldPlanCoverage(DashboardPlan plan) {
        if (!plan.selectedDateAvailable() || plan.operatingDay() == null) {
            return false;
        }

        return plan.operatingDay().status() == ParkOperatingStatus.OPEN
                || plan.calendarOverrideActive();
    }

    public static TransportationDemandPeriod demandPeriod(
            DashboardPlan plan,
            ShuttlePeriod period) {

        return switch (period) {
            case MORNING -> plan.transportationDemand().getMorning();
            case MIDDAY -> plan.transportationDemand().getMidday();
            case AFTERNOON -> plan.transportationDemand().getEvening();
        };
    }

    public static LocalTime effectiveStart(
            DashboardPlan plan,
            DriverAssignment assignment) {

        if (assignment.getScheduledStartTime() != null) {
            return assignment.getScheduledStartTime();
        }

        TransportationDemandPeriod demandPeriod =
                demandPeriod(plan, assignment.getPeriod());

        return demandPeriod == null
                ? null
                : demandPeriod.getRecommendedStartTime();
    }

    public static LocalTime effectiveEnd(
            DashboardPlan plan,
            DriverAssignment assignment) {

        if (assignment.getScheduledEndTime() != null) {
            return assignment.getScheduledEndTime();
        }

        TransportationDemandPeriod demandPeriod =
                demandPeriod(plan, assignment.getPeriod());

        return demandPeriod == null
                ? null
                : demandPeriod.getRecommendedEndTime();
    }

    public static LocalDateTime scheduledStart(
            DashboardPlan plan,
            DriverAssignment assignment) {

        LocalTime start = effectiveStart(plan, assignment);

        return start == null
                ? null
                : LocalDateTime.of(assignment.getAssignmentDate(), start);
    }

    public static LocalDateTime scheduledEnd(
            DashboardPlan plan,
            DriverAssignment assignment) {

        LocalTime start = effectiveStart(plan, assignment);
        LocalTime end = effectiveEnd(plan, assignment);

        if (end == null) {
            return null;
        }

        LocalDate endDate = assignment.getAssignmentDate();
        if (start != null && !end.isAfter(start)) {
            endDate = endDate.plusDays(1);
        }

        return LocalDateTime.of(endDate, end);
    }

    public static boolean assignedDriverStillMatchesCurrentSchedule(
            DashboardPlan plan,
            DriverAssignment assignment) {

        List<DriverSchedule> driverSchedules = plan.driverSchedules().stream()
                .filter(schedule -> schedule.driverId().equals(
                        assignment.getScheduledDriver().getId()
                ))
                .toList();

        if (driverSchedules.isEmpty()) {
            return false;
        }

        LocalTime requestedStart = effectiveStart(plan, assignment);
        LocalTime requestedEnd = effectiveEnd(plan, assignment);

        if (requestedStart == null || requestedEnd == null) {
            return true;
        }

        return driverSchedules.stream()
                .anyMatch(schedule -> schedule.covers(
                        requestedStart,
                        requestedEnd
                ));
    }

    public static boolean hasEligibleDriver(
            List<DriverSchedule> schedules,
            TransportationDemandPeriod demandPeriod) {

        if (schedules == null || schedules.isEmpty()) {
            return false;
        }

        if (!demandPeriod.hasRecommendedWindow()) {
            return true;
        }

        return schedules.stream()
                .anyMatch(schedule -> schedule.overlaps(
                        demandPeriod.getRecommendedStartTime(),
                        demandPeriod.getRecommendedEndTime()
                ));
    }

    public static String requestedWindowLabel(
            DashboardPlan plan,
            DriverAssignment assignment) {

        LocalTime start = effectiveStart(plan, assignment);
        LocalTime end = effectiveEnd(plan, assignment);

        if (start == null || end == null) {
            return null;
        }

        return formatTime(start) + "–" + formatTime(end);
    }

    public static String requestedWindowLabel(
            TransportationDemandPeriod demandPeriod) {

        if (demandPeriod == null || !demandPeriod.hasRecommendedWindow()) {
            return null;
        }

        return formatTime(demandPeriod.getRecommendedStartTime())
                + "–"
                + formatTime(demandPeriod.getRecommendedEndTime());
    }

    public static String periodLabel(ShuttlePeriod period) {
        return switch (period) {
            case MORNING -> "Morning";
            case MIDDAY -> "Midday Turnover";
            case AFTERNOON -> "Afternoon";
        };
    }

    public static String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    public static String dashboardHref(DashboardPlan plan, String view) {
        LocalDate date = plan.selectedDate();

        if (date == null) {
            return "/app#" + view;
        }

        String href = "/app?date=" + date;

        if (plan.calendarOverrideActive()) {
            href += "&allowUnverifiedCalendar=true";
        }

        return href + "#" + view;
    }
}
