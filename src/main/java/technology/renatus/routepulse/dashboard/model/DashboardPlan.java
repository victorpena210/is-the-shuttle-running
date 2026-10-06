package technology.renatus.routepulse.dashboard.model;

import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
import technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy;
import java.time.LocalDate;
import technology.renatus.routepulse.wardrobe.*;
import technology.renatus.routepulse.assignments.service.AssignmentPlanningService;
import java.util.List;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.model.ParkCalendarOverride;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

/** Data for one planning date, including calendar verification and existing coverage. */
public record DashboardPlan(
        LocalDate selectedDate, LocalDate currentOperationsDate,
        List<LocalDate> availableDates, boolean selectedDateAvailable,
        boolean scheduleUploaded, List<ScheduleImport> recentScheduleImports,
        TransportationDemandAnalysis transportationDemand,
        List<DriverSchedule> driverSchedules, List<Driver> driversWithoutSchedules,
        List<Driver> activeDrivers, List<DriverAssignment> driverAssignments,
        List<ScheduleReconciliation> scheduleReconciliations,
        ScheduleImport latestScheduleImport, boolean assignmentReviewRequired,
        String assignmentMessage, ParkOperatingDay operatingDay,
        String calendarMessage, ParkCalendarOverride calendarOverride,
        boolean calendarOverrideActive, DriverAssignment middaySuggestion, WardrobeHoursService.Hours wardrobeHours) {
    public DashboardPlan(LocalDate selectedDate, LocalDate currentOperationsDate,
            List<LocalDate> availableDates, boolean selectedDateAvailable,
            boolean scheduleUploaded, List<ScheduleImport> recentScheduleImports,
            TransportationDemandAnalysis transportationDemand,
            List<DriverSchedule> driverSchedules, List<Driver> driversWithoutSchedules,
            List<Driver> activeDrivers, List<DriverAssignment> driverAssignments,
            List<ScheduleReconciliation> scheduleReconciliations,
            ScheduleImport latestScheduleImport, boolean assignmentReviewRequired,
            String assignmentMessage, ParkOperatingDay operatingDay,
            String calendarMessage, ParkCalendarOverride calendarOverride, boolean calendarOverrideActive) {
        this(selectedDate, currentOperationsDate, availableDates, selectedDateAvailable, scheduleUploaded,
                recentScheduleImports, transportationDemand, driverSchedules, driversWithoutSchedules,
                activeDrivers, driverAssignments, scheduleReconciliations, latestScheduleImport,
                assignmentReviewRequired, assignmentMessage, operatingDay, calendarMessage,
                calendarOverride, calendarOverrideActive, null, WardrobeHoursService.DEFAULT);
    }

    public DashboardPlan(LocalDate selectedDate, LocalDate currentOperationsDate,
            List<LocalDate> availableDates, boolean selectedDateAvailable, boolean scheduleUploaded,
            List<ScheduleImport> recentScheduleImports, TransportationDemandAnalysis transportationDemand,
            List<DriverSchedule> driverSchedules, List<Driver> driversWithoutSchedules, List<Driver> activeDrivers,
            List<DriverAssignment> driverAssignments, List<ScheduleReconciliation> scheduleReconciliations,
            ScheduleImport latestScheduleImport, boolean assignmentReviewRequired, String assignmentMessage,
            ParkOperatingDay operatingDay, String calendarMessage, ParkCalendarOverride calendarOverride,
            boolean calendarOverrideActive, DriverAssignment middaySuggestion) {
        this(selectedDate, currentOperationsDate, availableDates, selectedDateAvailable, scheduleUploaded,
                recentScheduleImports, transportationDemand, driverSchedules, driversWithoutSchedules,
                activeDrivers, driverAssignments, scheduleReconciliations, latestScheduleImport,
                assignmentReviewRequired, assignmentMessage, operatingDay, calendarMessage,
                calendarOverride, calendarOverrideActive, middaySuggestion, WardrobeHoursService.DEFAULT);
    }

    public technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod eveningDrivingDemand() {
        return LateOpeningPolicy.drivingDemand(transportationDemand, driverSchedules, ShuttlePeriod.AFTERNOON, wardrobeHours.closingTime());
    }
    public technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod middayDrivingDemand() {
        return LateOpeningPolicy.drivingDemand(transportationDemand, driverSchedules, ShuttlePeriod.MIDDAY, wardrobeHours.closingTime());
    }
    public boolean drivingStartConflict() {
        return driverAssignments.stream().filter(a -> a.getAssignmentOutcome()
                != technology.renatus.routepulse.assignments.domain.AssignmentOutcome.CANCELLED
                && a.getAssignmentOutcome() != technology.renatus.routepulse.assignments.domain.AssignmentOutcome.EXCUSED)
                .anyMatch(a -> AssignmentPlanningService.hasDrivingConflict(a, transportationDemand,
                        driverSchedules, driverAssignments, wardrobeHours.closingTime()));
    }

    public boolean morningStaffingBlocked() {
        if (!transportationDemand.getMorning().hasDemand()) return false;
        var morning = driverAssignments.stream().filter(a -> a.getPeriod()
                == ShuttlePeriod.MORNING).findFirst();
        if (morning.isPresent()) {
            var a = morning.get();
            return !MorningStaffingPolicy.canAssign(
                    driverSchedules, driverAssignments, a.getScheduledDriver().getId(),
                    a.getScheduledStartTime(), a.getScheduledEndTime());
        }
        return !MorningStaffingPolicy.hasMorningStaffing(
                driverSchedules, driverAssignments);
    }

    public ShuttlePeriod lateOpeningPeriod() {
        return LateOpeningPolicy.openingPeriod(transportationDemand, driverSchedules);
    }

    public boolean openingStaffingBlocked() {
        var period = lateOpeningPeriod();
        if (period == null) return false;
        var demand = period == ShuttlePeriod.MIDDAY ? middayDrivingDemand() : eveningDrivingDemand();
        return demand.hasDemand() && !LateOpeningPolicy.hasSafeDemand(demand, driverSchedules,
                driverAssignments, period, wardrobeHours.closingTime());
    }

    public boolean requiresCoverage(ShuttlePeriod period) {
        if (period == lateOpeningPeriod()) {
            var demand = period == ShuttlePeriod.MIDDAY ? middayDrivingDemand() : eveningDrivingDemand();
            return demand.hasDemand() && !openingStaffingBlocked();
        }
        return switch (period) {
            case MORNING -> transportationDemand.getMorning().hasDemand() && !morningStaffingBlocked();
            case MIDDAY -> false;
            case AFTERNOON -> eveningDrivingDemand().hasDemand();
        };
    }
}
