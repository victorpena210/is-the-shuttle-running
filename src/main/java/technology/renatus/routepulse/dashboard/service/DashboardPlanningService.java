package technology.renatus.routepulse.dashboard.service;

import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
import technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.assignments.service.ScheduleReconciliationService;
import technology.renatus.routepulse.calendar.domain.ParkOperatingDay;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.calendar.model.ParkCalendarOverride;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
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

/** Loads the data for one dashboard date and applies the persisted calendar planning gate. */
@Service
public class DashboardPlanningService {

    private final TransportationDemandService transportationDemandService;
    private final CsvScheduleProvider csvScheduleProvider;
    private final DriverScheduleService driverScheduleService;
    private final ParkOperatingDayService parkOperatingDayService;
    private final ParkCalendarOverrideService parkCalendarOverrideService;
    private final DriverAssignmentService driverAssignmentService;
    private final DriverService driverService;
    private final ScheduleReconciliationService scheduleReconciliationService;

    public DashboardPlanningService(
            TransportationDemandService transportationDemandService,
            CsvScheduleProvider csvScheduleProvider,
            DriverScheduleService driverScheduleService,
            ParkOperatingDayService parkOperatingDayService,
            ParkCalendarOverrideService parkCalendarOverrideService,
            DriverAssignmentService driverAssignmentService,
            DriverService driverService,
            ScheduleReconciliationService scheduleReconciliationService) {
        this.transportationDemandService = transportationDemandService;
        this.csvScheduleProvider = csvScheduleProvider;
        this.driverScheduleService = driverScheduleService;
        this.parkOperatingDayService = parkOperatingDayService;
        this.parkCalendarOverrideService = parkCalendarOverrideService;
        this.driverAssignmentService = driverAssignmentService;
        this.driverService = driverService;
        this.scheduleReconciliationService = scheduleReconciliationService;
    }

    public DashboardPlan getPlan(Long organizationId, LocalDate date) {
        return buildPlan(organizationId, date, true);
    }

    /**
     * Rebuilds the dashboard view model without creating missing assignments.
     * Used by live attention polling so a background refresh cannot mutate coverage.
     */
    public DashboardPlan getReadOnlyPlan(Long organizationId, LocalDate date) {
        return buildPlan(organizationId, date, false);
    }

    private DashboardPlan buildPlan(
            Long organizationId,
            LocalDate date,
            boolean createMissingAssignments) {
        List<LocalDate> availableDates = csvScheduleProvider.getAvailableDates(organizationId);
        LocalDate selectedDate = date;
        if (selectedDate == null && !availableDates.isEmpty()) {
            selectedDate = csvScheduleProvider.getFirstDateOfLatestImport(organizationId);
        }
        if (selectedDate == null) {
            selectedDate = driverAssignmentService.getCurrentOperationsDate();
        }
        var wardrobeHours = driverAssignmentService.getWardrobeHours(organizationId, selectedDate);
        boolean selectedDateAvailable = availableDates.contains(selectedDate);
        TransportationDemandAnalysis demand = TransportationDemandAnalysis.empty();
        List<DriverSchedule> driverSchedules = List.of();
        List<Driver> driversWithoutSchedules = List.of();
        List<DriverAssignment> assignments = List.of();
        List<ScheduleReconciliation> scheduleReconciliations = List.of();
        List<Driver> activeDrivers = driverService.getActiveDrivers(organizationId);
        ScheduleImport latestImport = null;
        ParkOperatingDay operatingDay = null;
        ParkCalendarOverride calendarOverride = null;
        String assignmentMessage = null;
        DriverAssignment middaySuggestion = null;
        String calendarMessage = null;
        boolean reviewRequired = false;
        boolean overrideActive = false;

        if (selectedDateAvailable) {
            latestImport = csvScheduleProvider.getLatestImportForDate(organizationId, selectedDate).orElse(null);
            // Uploaded schedules determine demand, even when the calendar cannot be verified.
            demand = transportationDemandService.getDemandForDate(organizationId, selectedDate);
            operatingDay = parkOperatingDayService.getOperatingDay(selectedDate);
            calendarOverride = parkCalendarOverrideService
                    .findLatest(organizationId, selectedDate)
                    .orElse(null);
            overrideActive = calendarOverride != null
                    && operatingDay.status() != ParkOperatingStatus.OPEN
                    && calendarOverride.getOverrideStatus() == ParkOperatingStatus.OPEN;
            calendarMessage = describeCalendar(operatingDay, overrideActive);

            // Automatic coverage is allowed only by a verified OPEN result or a persisted manager override.
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
                        latestImport);
                reviewRequired = scheduleReconciliations.stream()
                        .anyMatch(ScheduleReconciliation::requiresAction);
                middaySuggestion = driverAssignmentService.getMiddaySuggestion(organizationId, selectedDate);
                boolean morningRequired = demand.getMorning().hasDemand()
                        && MorningStaffingPolicy
                                .hasMorningStaffing(driverSchedules, assignments);
                var opening = LateOpeningPolicy
                        .openingPeriod(demand, driverSchedules);
                var eveningDemand = LateOpeningPolicy
                        .drivingDemand(demand, driverSchedules, ShuttlePeriod.AFTERNOON, wardrobeHours.closingTime());
                boolean eveningRequired = eveningDemand.hasDemand() && (opening != ShuttlePeriod.AFTERNOON
                        || LateOpeningPolicy.hasSafeDemand(
                                eveningDemand, driverSchedules, assignments, opening, wardrobeHours.closingTime()));
                boolean middayRequired = opening == ShuttlePeriod.MIDDAY
                        && LateOpeningPolicy.hasSafeDemand(
                                demand.getMidday(), driverSchedules, assignments, opening, wardrobeHours.closingTime());
                int requiredPeriods = (morningRequired ? 1 : 0) + (middayRequired ? 1 : 0) + (eveningRequired ? 1 : 0);
                int coveredRequiredPeriods = (morningRequired && assignments.stream()
                        .anyMatch(a -> a.getPeriod() == ShuttlePeriod.MORNING) ? 1 : 0)
                        + (eveningRequired && assignments.stream()
                        .anyMatch(a -> a.getPeriod() == ShuttlePeriod.AFTERNOON) ? 1 : 0);
                coveredRequiredPeriods += middayRequired && assignments.stream()
                        .anyMatch(a -> a.getPeriod() == ShuttlePeriod.MIDDAY) ? 1 : 0;
                int missingRequiredPeriods = Math.max(0, requiredPeriods - coveredRequiredPeriods);

                if (missingRequiredPeriods == requiredPeriods && requiredPeriods > 0) {
                    assignmentMessage = "Manual coverage required — no eligible driver could cover the required driving windows.";
                } else if (missingRequiredPeriods == 1) {
                    assignmentMessage = "One shuttle period still needs manual coverage.";
                } else if (missingRequiredPeriods > 1) {
                    assignmentMessage = missingRequiredPeriods
                            + " shuttle periods still need manual coverage.";
                }
            }
        }

        return new DashboardPlan(
                selectedDate,
                driverAssignmentService.getCurrentOperationsDate(),
                availableDates,
                selectedDateAvailable,
                csvScheduleProvider.hasUploadedSchedule(organizationId),
                csvScheduleProvider.getRecentImports(organizationId),
                demand,
                driverSchedules,
                driversWithoutSchedules,
                activeDrivers,
                assignments,
                scheduleReconciliations,
                latestImport,
                reviewRequired,
                assignmentMessage,
                operatingDay,
                calendarMessage,
                calendarOverride,
                overrideActive, middaySuggestion, wardrobeHours);
    }

    private String describeCalendar(ParkOperatingDay day, boolean overrideActive) {
        if (overrideActive) {
            return day.status() == ParkOperatingStatus.CLOSED
                    ? "Calendar reported closed — manager override recorded"
                    : "Calendar unavailable — manager override recorded";
        }
        if (day.status() == ParkOperatingStatus.CLOSED) {
            return "Park closed — shuttle not scheduled";
        }
        if (day.status() == ParkOperatingStatus.UNKNOWN) {
            return "Calendar could not be verified — review required";
        }
        if (day.isManagerConfirmed()) {
            return "Open — saved legacy manager confirmation";
        }
        if (day.isPersistedSnapshot()) {
            return day.buyout()
                    ? "Open — saved Six Flags calendar · Private Buyout"
                    : "Open — saved Six Flags calendar";
        }
        return day.buyout()
                ? "Open — Private Buyout"
                : "Open — Public Operating Day";
    }
}
