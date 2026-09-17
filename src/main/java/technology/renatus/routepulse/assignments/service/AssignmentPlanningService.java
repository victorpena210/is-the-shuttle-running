package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.calendar.service.ParkOperatingDayService;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.planning.AssignmentWindowPlanner;
import technology.renatus.routepulse.assignments.service.planning.DriverScheduleSelector;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.DriverScheduleResult;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.DriverScheduleService;
import technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/**
 * Plans and rebalances automatic coverage while preserving protected assignments and history.
 *
 * Morning and afternoon are planned as mostly continuous driving windows with
 * one protected break when a demand-free window of at least 15 minutes exists.
 * Midday is intentionally planned as explicit demand-bearing start/stop runs.
 */
@Service
public class AssignmentPlanningService {

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final DriverScheduleService driverScheduleService;
    private final UploadedShiftRepository uploadedShiftRepository;
    private final DriverWorkloadService driverWorkloadService;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final TransportationDemandService transportationDemandService;
    private final DriverScheduleSelector scheduleSelector;
    private final AssignmentWindowPlanner windowPlanner;
    private final int protectedBreakMinutes;
    private final OrganizationRepository organizationRepository;
    private final ParkOperatingDayService operatingDayService;
    private final ParkCalendarOverrideService calendarOverrideService;
    private final Clock clock;

    public AssignmentPlanningService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            DriverScheduleService driverScheduleService,
            UploadedShiftRepository uploadedShiftRepository,
            DriverWorkloadService driverWorkloadService,
            MorningDrivingPlanningService morningDrivingPlanningService,
            TransportationDemandService transportationDemandService,
            @Value("${app.driver-break.minimum-free-minutes:15}") int protectedBreakMinutes,
            OrganizationRepository organizationRepository,
            ParkOperatingDayService operatingDayService,
            ParkCalendarOverrideService calendarOverrideService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.driverScheduleService = driverScheduleService;
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.driverWorkloadService = driverWorkloadService;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.transportationDemandService = transportationDemandService;
        this.scheduleSelector = new DriverScheduleSelector();
        this.windowPlanner = new AssignmentWindowPlanner();
        this.protectedBreakMinutes = Math.max(0, protectedBreakMinutes);
        this.organizationRepository = organizationRepository;
        this.operatingDayService = operatingDayService;
        this.calendarOverrideService = calendarOverrideService;
        this.clock = Clock.system(ZoneId.of(operationsZone));
    }

    @Transactional
    public List<DriverAssignment> getOrCreateAssignments(
            Long organizationId,
            LocalDate assignmentDate) {

        LocalDateTime now = LocalDateTime.now(clock);
        if (assignmentDate.isBefore(now.toLocalDate())) {
            return orderByPeriod(assignmentRepository
                    .findAllByOrganization_IdAndAssignmentDate(organizationId, assignmentDate));
        }

        // Serialize planning even when a date has no assignment rows to lock yet.
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization was not found."));

        // Always plan the uploaded horizon chronologically. Viewing a later tab first
        // must produce the same rotation, and a revised Friday must update Saturday too.
        for (LocalDate date : uploadedShiftRepository.findAvailableDates(organizationId).stream()
                .filter(date -> !date.isBefore(now.toLocalDate())).distinct().sorted().toList()) {
            if (isPlanningAllowed(organizationId, date)) {
                planDate(organizationId, date, now);
            }
        }
        return orderByPeriod(assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, assignmentDate));
    }

    private boolean isPlanningAllowed(Long organizationId, LocalDate date) {
        var override = calendarOverrideService.findLatest(organizationId, date);
        if (override.isPresent()) {
            return override.get().getOverrideStatus() == ParkOperatingStatus.OPEN;
        }
        return operatingDayService.getOperatingDay(date).status() == ParkOperatingStatus.OPEN;
    }

    private void planDate(Long organizationId, LocalDate date, LocalDateTime now) {
        List<DriverAssignment> existing = assignmentRepository
                .findAllForUpdateByOrganizationAndDate(organizationId, date);
        DriverScheduleResult result = driverScheduleService.getDriverSchedulesForDate(organizationId, date);
        Map<Long, List<DriverSchedule>> schedules = result.scheduledDrivers().stream()
                .collect(Collectors.groupingBy(DriverSchedule::driverId));
        List<DriverSchedule> eligible = schedules.values().stream().map(scheduleSelector::earliest).toList();
        if (eligible.isEmpty()) {
            return; // Keep existing coverage visible to schedule reconciliation.
        }

        MorningDrivingPlan morningPlan = morningDrivingPlanningService.getPlanForDate(organizationId, date);
        TransportationDemandAnalysis demand = transportationDemandService.getDemandForDate(organizationId, date);
        ScheduleImport sourceImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(organizationId, date).orElse(null);

        List<DriverAssignment> assigned = new ArrayList<>(existing.stream()
                .filter(assignment -> !assignment.canAutomaticallyRebalance(now)).toList());
        List<DriverAssignment> additions = new ArrayList<>();
        for (ShuttlePeriod period : List.of(ShuttlePeriod.MORNING, ShuttlePeriod.AFTERNOON, ShuttlePeriod.MIDDAY)) {
            DriverAssignment original = findAssignment(existing, period);
            if (original != null && !original.canAutomaticallyRebalance(now)) {
                continue;
            }
            List<DriverSchedule> ranked = driverWorkloadService.rankEligibleDrivers(
                    organizationId, date, eligible, assigned);
            Set<Long> used = new HashSet<>();
            assigned.stream().filter(assignment -> assignment.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                    && assignment.getAssignmentOutcome() != AssignmentOutcome.EXCUSED)
                    .forEach(assignment -> addDriverId(used, assignment));
            DriverAssignment plan;
            if (period == ShuttlePeriod.MORNING) {
                // Reserve flexible drivers for later duties. A 9–3 driver can take
                // morning so a 9–9 driver remains available for the full midday/evening.
                // Stable sorting keeps fairness order among equally flexible drivers.
                ranked = ranked.stream().sorted(Comparator.comparing(
                        (DriverSchedule schedule) -> used.contains(schedule.driverId()))
                        .thenComparingInt(schedule ->
                                (findAssignment(assigned, ShuttlePeriod.AFTERNOON) == null
                                        && coversPeriod(schedules.get(schedule.driverId()), demand.getEvening()) ? 1 : 0)
                                + (findAssignment(assigned, ShuttlePeriod.MIDDAY) == null
                                        && coversPeriod(schedules.get(schedule.driverId()), demand.getMidday()) ? 1 : 0)))
                        .toList();
                plan = createMorningAssignment(organizationId, date, ranked, schedules,
                        morningPlan, demand.getMorning(), sourceImport);
            } else if (period == ShuttlePeriod.AFTERNOON) {
                if (findAssignment(assigned, ShuttlePeriod.MIDDAY) == null) {
                    ranked = ranked.stream().sorted(Comparator.comparing(
                            schedule -> coversPeriod(schedules.get(schedule.driverId()), demand.getMidday())))
                            .toList();
                }
                plan = createContinuousPeriodAssignment(organizationId, date, period, demand.getEvening(),
                        ranked, schedules, used, sourceImport);
            } else {
                DriverAssignment morning = findAssignment(assigned, ShuttlePeriod.MORNING);
                plan = createMiddayAssignment(organizationId, date, demand.getMidday(), ranked, schedules,
                        used, morning == null ? null : morning.getScheduledDriver().getId(), sourceImport);
            }
            if (plan == null) {
                if (original != null) assigned.add(original);
                continue;
            }
            // Never retroactively create/reassign a window whose start has already passed today.
            if (date.equals(now.toLocalDate()) && (plan.getScheduledStartTime() == null
                    || !plan.getScheduledStartTime().isAfter(now.toLocalTime()))) {
                if (original != null) assigned.add(original);
                continue;
            }
            if (original == null) {
                additions.add(plan);
                assigned.add(plan);
            } else {
                original.applyAutomaticPlan(plan, now, clock.instant());
                assigned.add(original);
            }
        }
        if (!additions.isEmpty()) assignmentRepository.saveAll(additions);
        // Make this day's allocations available to the next day's workload query.
        assignmentRepository.flush();
    }

    private boolean coversPeriod(List<DriverSchedule> schedules, TransportationDemandPeriod demand) {
        return demand != null && demand.hasDemand() && demand.getWindows().stream()
                .filter(window -> window.getDemandCount() > 0)
                .allMatch(window -> schedules.stream().anyMatch(schedule ->
                        schedule.covers(window.getWindowStart(), window.getWindowEnd())));
    }

    private DriverAssignment createMorningAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            MorningDrivingPlan morningPlan,
            TransportationDemandPeriod morningDemand,
            ScheduleImport sourceScheduleImport) {

        if (morningPlan == null || !morningPlan.hasDemand()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseMorning(
                fairestOrder,
                schedulesByDriverId,
                morningPlan
        );
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.MORNING,
                driver,
                sourceScheduleImport
        );

        windowPlanner.applyContinuousPlanWithBreak(
                assignment,
                driverSchedule,
                morningPlan.getScheduledStartTime(),
                morningPlan.getScheduledEndTime(),
                morningDemand.getWindows(),
                protectedBreakMinutes
        );
        return assignment;
    }

    private DriverAssignment createContinuousPeriodAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            ShuttlePeriod period,
            TransportationDemandPeriod demandPeriod,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            Set<Long> avoidDriverIds,
            ScheduleImport sourceScheduleImport) {

        if (demandPeriod == null
                || !demandPeriod.hasDemand()
                || !demandPeriod.hasRecommendedWindow()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseForWindow(
                fairestOrder,
                schedulesByDriverId,
                demandPeriod.getRecommendedStartTime(),
                demandPeriod.getRecommendedEndTime(),
                avoidDriverIds
        );
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                period,
                driver,
                sourceScheduleImport
        );

        windowPlanner.applyContinuousPlanWithBreak(
                assignment,
                driverSchedule,
                demandPeriod.getRecommendedStartTime(),
                demandPeriod.getRecommendedEndTime(),
                demandPeriod.getWindows(),
                protectedBreakMinutes
        );
        return assignment;
    }

    private DriverAssignment createMiddayAssignment(
            Long organizationId,
            LocalDate assignmentDate,
            TransportationDemandPeriod middayDemand,
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            Set<Long> avoidDriverIds,
            Long overlappingMorningDriverId,
            ScheduleImport sourceScheduleImport) {

        if (middayDemand == null
                || !middayDemand.hasDemand()
                || !middayDemand.hasRecommendedWindow()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseForDemandWindows(
                fairestOrder,
                schedulesByDriverId,
                middayDemand.getWindows(),
                avoidDriverIds
        );

        // Prefer a third driver when one is available. If not, allow the
        // afternoon driver before falling back to the morning driver because
        // morning can overlap the first midday turnover window around noon.
        if (driverSchedule == null && overlappingMorningDriverId != null) {
            driverSchedule = scheduleSelector.chooseForDemandWindows(
                    fairestOrder,
                    schedulesByDriverId,
                    middayDemand.getWindows(),
                    Set.of(overlappingMorningDriverId)
            );
        }
        if (driverSchedule == null) {
            driverSchedule = scheduleSelector.chooseForDemandWindows(
                    fairestOrder,
                    schedulesByDriverId,
                    middayDemand.getWindows(),
                    Set.of()
            );
        }
        if (driverSchedule == null) {
            return null;
        }

        Driver driver = loadDriver(organizationId, driverSchedule.driverId());
        DriverAssignment assignment = DriverAssignment.automatic(
                assignmentDate,
                ShuttlePeriod.MIDDAY,
                driver,
                sourceScheduleImport
        );

        if (!windowPlanner.applyMiddayPlan(
                assignment,
                schedulesByDriverId.getOrDefault(driverSchedule.driverId(), List.of()),
                middayDemand.getWindows())) {
            return null;
        }
        return assignment;
    }

    private DriverAssignment findAssignment(
            List<DriverAssignment> assignments,
            ShuttlePeriod period) {
        return assignments.stream()
                .filter(assignment -> assignment.getPeriod() == period)
                .findFirst()
                .orElse(null);
    }

    private void addDriverId(Set<Long> usedDriverIds, DriverAssignment assignment) {
        if (assignment != null && assignment.getScheduledDriver() != null) {
            usedDriverIds.add(assignment.getScheduledDriver().getId());
        }
    }

    private Driver loadDriver(Long organizationId, Long driverId) {
        return driverRepository.findByIdAndOrganization_Id(driverId, organizationId)
                .orElseThrow(() -> new IllegalStateException("Eligible driver was not found."));
    }

    private List<DriverAssignment> orderByPeriod(List<DriverAssignment> assignments) {
        return assignments.stream()
                .sorted(Comparator.comparingInt(assignment -> assignment.getPeriod().ordinal()))
                .toList();
    }
}
