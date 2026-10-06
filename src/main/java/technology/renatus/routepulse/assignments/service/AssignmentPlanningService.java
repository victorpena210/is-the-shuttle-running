package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import technology.renatus.routepulse.wardrobe.*;
import java.time.LocalTime;
import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
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
 * Midday is offered as an optional, unsaved suggestion until a manager accepts it.
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
    private final WardrobeHoursService wardrobeHoursService;

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
            WardrobeHoursService wardrobeHoursService,
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
        this.wardrobeHoursService = wardrobeHoursService;
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
        lockPlanning(organizationId);

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

    @Transactional(readOnly = true)
    public WardrobeHoursService.Hours getWardrobeHours(Long organizationId, LocalDate date) {
        return wardrobeHoursService.getHours(organizationId, date);
    }

    @Transactional
    public void updateWardrobeHours(Long organizationId, WardrobeHoursRule.Scope scope, String label,
            LocalDate from, LocalDate to, LocalTime closingTime, String actorEmail) {
        wardrobeHoursService.save(organizationId, scope, label, from, to, closingTime, actorEmail);
        getOrCreateAssignments(organizationId, from.isBefore(LocalDate.now(clock)) ? LocalDate.now(clock) : from);
    }

    public static boolean startsBeforeClosing(DriverAssignment assignment, LocalTime closingTime) {
        return assignment.getPeriod() == ShuttlePeriod.AFTERNOON && assignment.getScheduledStartTime() != null
                && (closingTime.equals(LocalTime.MIDNIGHT) || assignment.getScheduledStartTime().isBefore(closingTime));
    }

    public void validateEveningClosingTime(Long organizationId, DriverAssignment assignment) {
        if (!assignment.getAssignmentDate().isBefore(LocalDate.now(clock))
                && startsBeforeClosing(assignment, wardrobeHoursService.getHours(organizationId, assignment.getAssignmentDate()).closingTime())) {
            throw new IllegalStateException("Evening driving cannot start before wardrobe closes. Update wardrobe hours or choose a later start.");
        }
    }

    public void lockPlanning(Long organizationId) {
        organizationRepository.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalStateException("Organization was not found."));
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

        MorningDrivingPlan morningPlan = morningDrivingPlanningService.getPlanForDate(organizationId, date);
        TransportationDemandAnalysis demand = transportationDemandService.getDemandForDate(organizationId, date);
        var eveningDemand = EveningClosingPolicy.afterClosing(demand.getEvening(),
                wardrobeHoursService.getHours(organizationId, date).closingTime());
        ScheduleImport sourceImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(organizationId, date).orElse(null);

        // Withdraw only untouched future automatic plans. Manager decisions and driving history survive.
        for (DriverAssignment assignment : existing) {
            if (assignment.getPeriod() == ShuttlePeriod.MIDDAY && assignment.canAutomaticallyRebalance(now)) {
                assignmentRepository.delete(assignment);
            }
        }
        existing = existing.stream().filter(a -> a.getPeriod() != ShuttlePeriod.MIDDAY
                || !a.canAutomaticallyRebalance(now)).toList();
        List<DriverAssignment> assigned = new ArrayList<>(existing.stream()
                .filter(assignment -> !assignment.canAutomaticallyRebalance(now)).toList());
        List<DriverAssignment> additions = new ArrayList<>();
        for (ShuttlePeriod period : List.of(ShuttlePeriod.MORNING, ShuttlePeriod.AFTERNOON)) {
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
                // Preserve evening availability; optional midday does not reserve a driver.
                ranked = ranked.stream().sorted(Comparator.comparing(
                        (DriverSchedule schedule) -> used.contains(schedule.driverId()))
                        .thenComparing(schedule -> findAssignment(assigned, ShuttlePeriod.AFTERNOON) == null
                                && coversPeriod(schedules.get(schedule.driverId()), eveningDemand)))
                        .toList();
                plan = createMorningAssignment(organizationId, date, ranked, schedules,
                        morningPlan, demand.getMorning(), sourceImport, assigned);
            } else {
                var upcomingEvening = eveningDemand;
                if (date.equals(now.toLocalDate())) {
                    LocalTime nextMinute = now.toLocalTime().withSecond(0).withNano(0).plusMinutes(1);
                    upcomingEvening = nextMinute.equals(LocalTime.MIDNIGHT)
                            ? EveningClosingPolicy.afterClosing(eveningDemand, LocalTime.MIDNIGHT)
                            : EveningClosingPolicy.notBefore(eveningDemand, nextMinute);
                }
                plan = createContinuousPeriodAssignment(organizationId, date, period, upcomingEvening,
                        ranked, schedules, used, sourceImport);
            }
            if (plan == null) {
                if (original != null) {
                    if (period == ShuttlePeriod.AFTERNOON && (!eveningDemand.hasDemand()
                            || startsBeforeClosing(original, wardrobeHoursService.getHours(organizationId, date).closingTime()))) {
                        assignmentRepository.delete(original);
                    } else if (period == ShuttlePeriod.MORNING && !MorningStaffingPolicy.canAssign(
                            result.scheduledDrivers(), assigned, original.getScheduledDriver().getId(),
                            original.getScheduledStartTime(), original.getScheduledEndTime())) {
                        assignmentRepository.delete(original);
                    } else assigned.add(original);
                }
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

    @Transactional(readOnly = true)
    public DriverAssignment getMiddaySuggestion(Long organizationId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (date.isBefore(now.toLocalDate()) || !isPlanningAllowed(organizationId, date)) return null;
        List<DriverAssignment> assigned = assignmentRepository
                .findAllByOrganization_IdAndAssignmentDate(organizationId, date);
        if (findAssignment(assigned, ShuttlePeriod.MIDDAY) != null) return null;
        var rows = driverScheduleService.getDriverSchedulesForDate(organizationId, date).scheduledDrivers();
        Map<Long, List<DriverSchedule>> schedules = rows.stream().collect(Collectors.groupingBy(DriverSchedule::driverId));
        var eligible = schedules.values().stream().map(scheduleSelector::earliest).toList();
        var ranked = driverWorkloadService.rankEligibleDrivers(organizationId, date, eligible, assigned);
        var demand = transportationDemandService.getDemandForDate(organizationId, date).getMidday();
        // Do not recommend a driver whose other assignment overlaps any proposed run.
        ranked = ranked.stream().filter(row -> {
            var candidate = createMiddayAssignment(organizationId, date, demand, List.of(row), schedules, null);
            if (candidate == null) return false;
            var morning = findAssignment(assigned, ShuttlePeriod.MORNING);
            if (morning != null && affectsMorningCoverage(candidate, morning)) {
                var withSuggestion = new ArrayList<>(assigned);
                withSuggestion.add(candidate);
                if (!MorningStaffingPolicy.canAssign(rows, withSuggestion, morning.getScheduledDriver().getId(),
                        morning.getScheduledStartTime(), morning.getScheduledEndTime())) return false;
            }
            return assigned.stream()
                    .filter(a -> a.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                            && a.getAssignmentOutcome() != AssignmentOutcome.EXCUSED
                            && a.getAssignmentOutcome() != AssignmentOutcome.MISSED)
                    .filter(a -> a.getScheduledDriver().getId().equals(row.driverId()))
                    .noneMatch(a -> candidate.getPlannedDrivingWindows().stream().anyMatch(w ->
                            a.hasPlannedDrivingWindows() ? a.getPlannedDrivingWindows().stream().anyMatch(other ->
                                    other.getStartTime().isBefore(w.getEndTime()) && other.getEndTime().isAfter(w.getStartTime()))
                                    : a.getScheduledStartTime() != null && a.getScheduledEndTime() != null
                                    && a.getScheduledStartTime().isBefore(w.getEndTime()) && a.getScheduledEndTime().isAfter(w.getStartTime())));
        }).toList();
        DriverAssignment suggestion = createMiddayAssignment(organizationId, date, demand, ranked, schedules,
                uploadedShiftRepository.findLatestScheduleImportForDate(organizationId, date).orElse(null));
        if (suggestion == null || (date.equals(now.toLocalDate())
                && !suggestion.getScheduledStartTime().isAfter(now.toLocalTime()))) return null;
        return suggestion;
    }

    public static String suggestionKey(DriverAssignment suggestion) {
        if (suggestion == null) return "";
        return suggestion.getScheduledDriver().getId() + ":" + suggestion.getAssignmentDate() + ":"
                + (suggestion.getSourceScheduleImport() == null ? "" : suggestion.getSourceScheduleImport().getId()) + ":"
                + suggestion.getPlannedDrivingWindows().stream()
                        .map(w -> w.getStartTime() + "-" + w.getEndTime()).collect(Collectors.joining(","));
    }

    @Transactional
    public DriverAssignment acceptMiddaySuggestion(Long organizationId, LocalDate date, String expectedPlan) {
        // Same organization lock as automatic planning; recheck availability and fairness at acceptance.
        getOrCreateAssignments(organizationId, date);
        DriverAssignment suggestion = getMiddaySuggestion(organizationId, date);
        if (suggestion == null || !suggestionKey(suggestion).equals(expectedPlan)) {
            throw new IllegalStateException("The midday suggestion changed or is no longer available. Refresh coverage and review it again.");
        }
        DriverAssignment assignment = DriverAssignment.manual(date, ShuttlePeriod.MIDDAY,
                suggestion.getScheduledDriver(), "Manager accepted the optional midday fairness suggestion.");
        assignment.updateScheduledTimes(suggestion.getScheduledStartTime(), suggestion.getScheduledEndTime());
        assignment.replacePlannedDrivingWindows(suggestion.getPlannedDrivingWindows());
        validateMorningCoverageAfterChange(organizationId, assignment);
        return assignmentRepository.save(assignment);
    }

    /** Enforce the office constraint for manager edits as well as automatic generation. */
    public void validateMorningCoverageAfterChange(Long organizationId, DriverAssignment proposed) {
        if (proposed.getAssignmentDate().isBefore(LocalDate.now(clock))) return;
        var assignments = new ArrayList<>(assignmentRepository.findAllByOrganization_IdAndAssignmentDate(
                organizationId, proposed.getAssignmentDate()));
        assignments.removeIf(a -> a == proposed || (proposed.getId() != null && proposed.getId().equals(a.getId())));
        assignments.add(proposed);
        var morning = findAssignment(assignments, ShuttlePeriod.MORNING);
        if (morning == null || (morning != proposed && !affectsMorningCoverage(proposed, morning))) return;
        if (morning.getAssignmentOutcome() != AssignmentOutcome.PENDING
                && morning.getAssignmentOutcome() != AssignmentOutcome.IN_PROGRESS) return;
        var schedules = driverScheduleService.getDriverSchedulesForDate(organizationId, proposed.getAssignmentDate()).scheduledDrivers();
        if (!MorningStaffingPolicy.canAssign(schedules, assignments, morning.getScheduledDriver().getId(),
                morning.getScheduledStartTime(), morning.getScheduledEndTime())) {
            throw new IllegalStateException(MorningStaffingPolicy.MESSAGE);
        }
    }

    private boolean affectsMorningCoverage(DriverAssignment proposed, DriverAssignment morning) {
        LocalTime start = LocalTime.of(9, 0), end = LocalTime.NOON;
        if (morning.getScheduledStartTime() != null && morning.getScheduledStartTime().isBefore(start))
            start = morning.getScheduledStartTime();
        if (morning.getScheduledEndTime() != null && morning.getScheduledEndTime().isAfter(end))
            end = morning.getScheduledEndTime();
        final LocalTime from = start, to = end;
        if (proposed.hasPlannedDrivingWindows()) return proposed.getPlannedDrivingWindows().stream()
                .anyMatch(w -> w.getStartTime().isBefore(to) && w.getEndTime().isAfter(from));
        return proposed.getScheduledStartTime() != null && proposed.getScheduledEndTime() != null
                && proposed.getScheduledStartTime().isBefore(to) && proposed.getScheduledEndTime().isAfter(from);
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
            ScheduleImport sourceScheduleImport,
            List<DriverAssignment> otherAssignments) {

        List<DriverSchedule> allSchedules = schedulesByDriverId.values().stream().flatMap(List::stream).toList();
        if (!MorningStaffingPolicy.hasMorningStaffing(allSchedules, otherAssignments)) return null;
        if (morningPlan == null || !morningPlan.hasDemand()) {
            return null;
        }

        Map<Long, List<DriverSchedule>> safeSchedules = new java.util.HashMap<>();
        schedulesByDriverId.forEach((id, rows) -> safeSchedules.put(id, rows.stream().filter(row -> {
            var clipped = row.clipTo(morningPlan.getScheduledStartTime(), morningPlan.getScheduledEndTime());
            return clipped != null && MorningStaffingPolicy.canAssign(allSchedules, otherAssignments,
                    id, clipped.startTime(), clipped.endTime());
        }).toList()));
        DriverSchedule driverSchedule = scheduleSelector.chooseMorning(fairestOrder, safeSchedules, morningPlan);
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
            ScheduleImport sourceScheduleImport) {

        if (middayDemand == null
                || !middayDemand.hasDemand()
                || !middayDemand.hasRecommendedWindow()) {
            return null;
        }

        DriverSchedule driverSchedule = scheduleSelector.chooseForDemandWindows(
                fairestOrder, schedulesByDriverId, middayDemand.getWindows(), Set.of());
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
