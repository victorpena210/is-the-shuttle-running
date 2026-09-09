package technology.renatus.routepulse.dashboard.service;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.assignments.service.ScheduleReconciliationService;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.dashboard.model.DashboardPlan;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;

/** Loads the data for one dashboard date and applies the existing calendar planning gate. */
@Service
public class DashboardPlanningService {

    private final TransportationDemandService transportationDemandService;
    private final CsvScheduleProvider csvScheduleProvider;
    private final DriverScheduleService driverScheduleService;
    private final ParkOperatingDayService parkOperatingDayService;
    private final DriverAssignmentService driverAssignmentService;
    private final DriverService driverService;
    private final ScheduleReconciliationService scheduleReconciliationService;

    public DashboardPlanningService(
            TransportationDemandService transportationDemandService,
            CsvScheduleProvider csvScheduleProvider,
            DriverScheduleService driverScheduleService,
            ParkOperatingDayService parkOperatingDayService,
            DriverAssignmentService driverAssignmentService,
            DriverService driverService,
            ScheduleReconciliationService scheduleReconciliationService) {
        this.transportationDemandService = transportationDemandService;
        this.csvScheduleProvider = csvScheduleProvider;
        this.driverScheduleService = driverScheduleService;
        this.parkOperatingDayService = parkOperatingDayService;
        this.driverAssignmentService = driverAssignmentService;
        this.driverService = driverService;
        this.scheduleReconciliationService = scheduleReconciliationService;
    }

    public DashboardPlan getPlan(
            Long organizationId,
            LocalDate date,
            boolean allowUnverifiedCalendar) {

        return buildPlan(organizationId, date, allowUnverifiedCalendar, true);
    }

    /**
     * Rebuilds the dashboard view model without creating missing assignments.
     * Used by live attention polling so a background refresh cannot mutate coverage.
     */
    public DashboardPlan getReadOnlyPlan(
            Long organizationId,
            LocalDate date,
            boolean allowUnverifiedCalendar) {

        return buildPlan(organizationId, date, allowUnverifiedCalendar, false);
    }

    private DashboardPlan buildPlan(
            Long organizationId,
            LocalDate date,
            boolean allowUnverifiedCalendar,
            boolean createMissingAssignments) {
        List<LocalDate> availableDates = csvScheduleProvider.getAvailableDates(organizationId);
        LocalDate selectedDate = date;
        if (selectedDate == null && !availableDates.isEmpty()) {
            selectedDate = csvScheduleProvider.getFirstDateOfLatestImport(organizationId);
        }
        if (selectedDate == null) {
            selectedDate = driverAssignmentService.getCurrentOperationsDate();
        }
        boolean selectedDateAvailable = availableDates.contains(selectedDate);
        TransportationDemandAnalysis demand = TransportationDemandAnalysis.empty();
        List<DriverSchedule> driverSchedules = List.of();
        List<Driver> driversWithoutSchedules = List.of();
        List<DriverAssignment> assignments = List.of();
        List<ScheduleReconciliation> scheduleReconciliations = List.of();
        List<Driver> activeDrivers = driverService.getActiveDrivers(organizationId);
        ScheduleImport latestImport = null;
        ParkOperatingDay operatingDay = null;
        String assignmentMessage = null;
        String calendarMessage = null;
        boolean reviewRequired = false;
        boolean overrideActive = false;

        if (selectedDateAvailable) {
            latestImport = csvScheduleProvider.getLatestImportForDate(organizationId, selectedDate).orElse(null);
            // Uploaded schedules determine demand, even when the calendar cannot be verified.
            demand = transportationDemandService.getDemandForDate(organizationId, selectedDate);
            operatingDay = parkOperatingDayService.getOperatingDay(selectedDate);
            overrideActive = operatingDay.status() == ParkOperatingStatus.UNKNOWN && allowUnverifiedCalendar;
            calendarMessage = describeCalendar(operatingDay, overrideActive);

            // Preserve the calendar gate before creating any automatic coverage.
            if (operatingDay.status() == ParkOperatingStatus.OPEN || overrideActive) {
                DriverScheduleResult result = driverScheduleService.getDriverSchedulesForDate(organizationId, selectedDate);
                driverSchedules = result.scheduledDrivers();
                driversWithoutSchedules = result.driversWithoutSchedules();
                assignments = createMissingAssignments
                        ? driverAssignmentService.getOrCreateAssignments(organizationId, selectedDate)
                        : driverAssignmentService.getExistingAssignments(organizationId, selectedDate);
                scheduleReconciliations = scheduleReconciliationService.reconcile(
                        organizationId,
                        assignments,
                        driverSchedules,
                        demand,
                        latestImport
                );
                reviewRequired = scheduleReconciliations.stream()
                        .anyMatch(ScheduleReconciliation::requiresAction);
                if (assignments.isEmpty()) {
                    assignmentMessage = "Manual morning and afternoon coverage required — no eligible drivers are working this date.";
                } else if (assignments.size() == 1) {
                    assignmentMessage = "One shuttle period still needs manual coverage.";
                }
            }
        }

        return new DashboardPlan(selectedDate, driverAssignmentService.getCurrentOperationsDate(),
                availableDates, selectedDateAvailable, csvScheduleProvider.hasUploadedSchedule(organizationId),
                csvScheduleProvider.getRecentImports(organizationId), demand, driverSchedules,
                driversWithoutSchedules, activeDrivers, assignments, scheduleReconciliations, latestImport, reviewRequired,
                assignmentMessage, operatingDay, calendarMessage, overrideActive);
    }

    private String describeCalendar(ParkOperatingDay day, boolean overrideActive) {
        if (day.status() == ParkOperatingStatus.CLOSED) {
            return "Park closed — shuttle not scheduled";
        }
        if (day.status() == ParkOperatingStatus.UNKNOWN) {
            return overrideActive
                    ? "Calendar unverified — manager continued with uploaded schedule"
                    : "Calendar could not be verified — review required";
        }
        if (day.isManagerConfirmed()) {
            return "Open — manager confirmed after calendar verification failure";
        }
        if (day.isPersistedSnapshot()) {
            return day.buyout() ? "Open — saved Six Flags calendar · Private Buyout" : "Open — saved Six Flags calendar";
        }
        return day.buyout() ? "Open — Private Buyout" : "Open — Public Operating Day";
    }
}
