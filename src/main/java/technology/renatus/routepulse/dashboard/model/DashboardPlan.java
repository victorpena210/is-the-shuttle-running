package technology.renatus.routepulse.dashboard.model;

import java.time.LocalDate;
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
        boolean calendarOverrideActive) {
}
