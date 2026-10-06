package technology.renatus.routepulse.assignments.service;

import java.time.LocalDate;
import technology.renatus.routepulse.wardrobe.*;
import java.time.LocalTime;
import technology.renatus.routepulse.assignments.service.planning.MorningStaffingPolicy;
import technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy;
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
 * Midday is optional except when it is the opening period for an afternoon-start roster.
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
    @Transactional
    public void updateWardrobeHours(Long organizationId, technology.renatus.routepulse.wardrobe.WardrobeHoursRule.Scope scope,
            String label, LocalDate from, LocalDate to, LocalTime opening, LocalTime closing, Integer dayOfWeek, String actorEmail) {
        wardrobeHoursService.save(organizationId, scope, label, from, to, opening, closing, dayOfWeek, actorEmail);
        getOrCreateAssignments(organizationId, from.isBefore(LocalDate.now(clock)) ? LocalDate.now(clock) : from);
    }


    public static boolean startsBeforeClosing(DriverAssignment assignment, LocalTime closingTime) {
        return assignment.getPeriod() != ShuttlePeriod.MORNING && assignment.getScheduledStartTime() != null
                && (closingTime.equals(LocalTime.MIDNIGHT) || assignment.getScheduledStartTime().isBefore(closingTime));
    }

    public void validateDemandStartTime(Long organizationId, DriverAssignment assignment) {
        if (assignment.getAssignmentDate().isBefore(LocalDate.now(clock))) return;
        var hours = wardrobeHoursService.getHours(organizationId, assignment.getAssignmentDate());
        if (hours.usesOpeningWindow()) {
            var demand = transportationDemandService.getDemandForDate(organizationId, assignment.getAssignmentDate());
            var roster = driverScheduleService.getDriverSchedulesForDate(organizationId, assignment.getAssignmentDate()).scheduledDrivers();
            var existing = assignmentRepository.findAllByOrganization_IdAndAssignmentDate(organizationId, assignment.getAssignmentDate());
            if (OpeningCoveragePolicy.conflict(assignment, demand, roster, existing, hours))
                throw new IllegalStateException("Opening driving must fit the configured opening window and keep another roster member at the counter. Overlapping drivers must leave the counter staffed; one driver cannot cover overlapping assignments.");
            return;
        }
        if (assignment.getPeriod() == ShuttlePeriod.MORNING) return;
        var closing = hours.closingTime();
        var analysis = transportationDemandService.getDemandForDate(organizationId, assignment.getAssignmentDate());
        var rows = driverScheduleService.getDriverSchedulesForDate(organizationId, assignment.getAssignmentDate()).scheduledDrivers();
        var assignments = assignmentRepository.findAllByOrganization_IdAndAssignmentDate(organizationId, assignment.getAssignmentDate());
        if (hasDrivingConflict(assignment, analysis, rows, assignments, closing)) {
            throw new IllegalStateException("Driving must start with eligible demand. Before wardrobe closes, only opening coverage is allowed, "
                    + "with another scheduled driver-roster member available at the counter throughout each run.");
        }
    }

    public static boolean hasDrivingConflict(DriverAssignment assignment, TransportationDemandAnalysis analysis,
            List<DriverSchedule> rows, List<DriverAssignment> assignments, LocalTime closing) {
        if (assignment.getPeriod() == ShuttlePeriod.MORNING || assignment.getScheduledStartTime() == null) return false;
        if (LateOpeningPolicy.openingPeriod(analysis, rows) == assignment.getPeriod()) {
            var demand = LateOpeningPolicy.drivingDemand(analysis, rows, assignment.getPeriod(), closing);
            return !demand.hasDemand() || assignment.getScheduledStartTime().isBefore(demand.getRecommendedStartTime())
                    || !LateOpeningPolicy.canAssign(assignment, rows, assignments, closing);
        }
        return startsBeforeDrivingDemand(assignment,
                assignment.getPeriod() == ShuttlePeriod.MIDDAY ? analysis.getMidday() : analysis.getEvening(), closing);
    }

    /** Existing manager decisions stay saved and are flagged for review instead of silently rewritten. */
    public static boolean startsBeforeDrivingDemand(DriverAssignment assignment,
            TransportationDemandPeriod demand, LocalTime closing) {
        if (assignment.getPeriod() == ShuttlePeriod.MORNING || assignment.getScheduledStartTime() == null) return false;
        if (startsBeforeClosing(assignment, closing)) return true;
        // A manager can still arrange extra service when there is no forecast for the period.
        if (!demand.hasDemand()) return false;
        var eligible = EveningClosingPolicy.afterClosing(demand, closing);
        return !eligible.hasDemand() || assignment.getScheduledStartTime().isBefore(eligible.getRecommendedStartTime());
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
        var hours = wardrobeHoursService.getHours(organizationId, date);
        if (hours.usesOpeningWindow()) {
            planConfiguredDate(organizationId, date, now, existing, result.scheduledDrivers(), schedules, eligible, demand, hours);
            return;
        }
        var closing = hours.closingTime();
        var openingPeriod = LateOpeningPolicy.openingPeriod(demand, result.scheduledDrivers());
        var eveningDemand = LateOpeningPolicy.drivingDemand(demand, result.scheduledDrivers(), ShuttlePeriod.AFTERNOON, closing);
        ScheduleImport sourceImport = uploadedShiftRepository
                .findLatestScheduleImportForDate(organizationId, date).orElse(null);

        // Withdraw only untouched future automatic plans. Manager decisions and driving history survive.
        for (DriverAssignment assignment : existing) {
            if (assignment.getPeriod() == ShuttlePeriod.MIDDAY && openingPeriod != ShuttlePeriod.MIDDAY
                    && assignment.canAutomaticallyRebalance(now)) {
                assignmentRepository.delete(assignment);
            }
        }
        existing = existing.stream().filter(a -> a.getPeriod() != ShuttlePeriod.MIDDAY
                || openingPeriod == ShuttlePeriod.MIDDAY || !a.canAutomaticallyRebalance(now)).toList();
        List<DriverAssignment> assigned = new ArrayList<>(existing.stream()
                .filter(assignment -> !assignment.canAutomaticallyRebalance(now)).toList());
        List<DriverAssignment> additions = new ArrayList<>();
        for (ShuttlePeriod period : ShuttlePeriod.values()) {
            if (period == ShuttlePeriod.MIDDAY && openingPeriod != period) continue;
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
            } else if (period == openingPeriod) {
                var openingDemand = upcomingDemand(LateOpeningPolicy.drivingDemand(
                        demand, result.scheduledDrivers(), period, closing), date, now);
                plan = createOpeningAssignment(organizationId, date, period, openingDemand, ranked,
                        schedules, result.scheduledDrivers(), assigned, closing, sourceImport);
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
                    if (period == openingPeriod || (period == ShuttlePeriod.AFTERNOON && (!eveningDemand.hasDemand()
                            || hasDrivingConflict(original, demand, result.scheduledDrivers(), assigned, closing)))) {
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

    private void planConfiguredDate(Long organizationId, LocalDate date, LocalDateTime now,
            List<DriverAssignment> existing, List<DriverSchedule> rows, Map<Long, List<DriverSchedule>> schedules,
            List<DriverSchedule> eligible, TransportationDemandAnalysis analysis, WardrobeHoursService.Hours hours) {
        var source = uploadedShiftRepository.findLatestScheduleImportForDate(organizationId, date).orElse(null);
        var assigned = new ArrayList<>(existing.stream().filter(a -> !a.canAutomaticallyRebalance(now)).toList());
        var additions = new ArrayList<DriverAssignment>();
        var openingPeriod = OpeningCoveragePolicy.openingPeriod(hours);
        // Retire the old slot only when it is an untouched automatic plan.
        existing.stream().filter(a -> a.getPeriod() != openingPeriod && a.getPeriod() != ShuttlePeriod.AFTERNOON
                && a.canAutomaticallyRebalance(now)).forEach(assignmentRepository::delete);
        var existingEvening = findAssignment(existing, ShuttlePeriod.AFTERNOON);
        for (var period : List.of(openingPeriod, ShuttlePeriod.AFTERNOON)) {
            var original = findAssignment(existing, period);
            if (original != null && !original.canAutomaticallyRebalance(now)) continue;
            var demand = upcomingDemand(period == openingPeriod ? OpeningCoveragePolicy.opening(analysis, hours)
                    : OpeningCoveragePolicy.evening(analysis, hours), date, now);
            var ranked = driverWorkloadService.rankEligibleDrivers(organizationId, date, eligible, assigned);
            // Keep the existing evening driver when adding opening coverage; assign the other eligible driver.
            if (existingEvening != null) {
                ranked = ranked.stream().sorted(Comparator.comparing((DriverSchedule row) ->
                        period == openingPeriod
                                ? row.driverId().equals(existingEvening.getScheduledDriver().getId())
                                : !row.driverId().equals(existingEvening.getScheduledDriver().getId()))).toList();
            }
            DriverAssignment best = null;
            for (var row : ranked) {
                var candidate = createContinuousPeriodAssignment(organizationId, date, period, demand, List.of(row), schedules, Set.of(), source, period == openingPeriod);
                if (period == openingPeriod && (candidate == null
                        || !LateOpeningPolicy.canAssign(candidate, rows, assigned, hours.closingTime()))) {
                    // If counter staffing covers only part of the opening window, retain only safe demand runs.
                    candidate = createOpeningAssignment(organizationId, date, period, demand, List.of(row),
                            schedules, rows, assigned, hours.closingTime(), source);
                }
                if (candidate == null || OpeningCoveragePolicy.overlapsOtherService(candidate, assigned)) continue;
                if (best == null || candidate.getScheduledMinutes() > best.getScheduledMinutes()) best = candidate;
            }
            if (best == null) {
                if (original != null) assignmentRepository.delete(original);
            } else if (original == null) {
                assigned.add(best); additions.add(best);
            } else {
                original.applyAutomaticPlan(best, now, clock.instant()); assigned.add(original);
            }
        }
        if (!additions.isEmpty()) assignmentRepository.saveAll(additions);
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
        var analysis = transportationDemandService.getDemandForDate(organizationId, date);
        var hours = wardrobeHoursService.getHours(organizationId, date);
        if (hours.usesOpeningWindow() ? OpeningCoveragePolicy.openingPeriod(hours) == ShuttlePeriod.MIDDAY
                : LateOpeningPolicy.openingPeriod(analysis, rows) == ShuttlePeriod.MIDDAY) return null;
        Map<Long, List<DriverSchedule>> schedules = rows.stream().collect(Collectors.groupingBy(DriverSchedule::driverId));
        var eligible = schedules.values().stream().map(scheduleSelector::earliest).toList();
        var ranked = driverWorkloadService.rankEligibleDrivers(organizationId, date, eligible, assigned);
        var remainingDemand = hours.usesOpeningWindow() ? OpeningCoveragePolicy.midday(analysis, hours)
                : EveningClosingPolicy.afterClosing(analysis.getMidday(), hours.closingTime());
        if (date.equals(now.toLocalDate())) {
            LocalTime nextMinute = now.toLocalTime().withSecond(0).withNano(0).plusMinutes(1);
            remainingDemand = nextMinute.equals(LocalTime.MIDNIGHT)
                    ? EveningClosingPolicy.afterClosing(remainingDemand, LocalTime.MIDNIGHT)
                    : EveningClosingPolicy.notBefore(remainingDemand, nextMinute);
        }
        final var demand = remainingDemand;
        // Do not recommend a driver whose other assignment overlaps any proposed run.
        ranked = ranked.stream().filter(row -> {
            var candidate = createMiddayAssignment(organizationId, date, demand, List.of(row), schedules, null);
            if (candidate == null) return false;
            var morning = findAssignment(assigned, ShuttlePeriod.MORNING);
            if (hours.usesOpeningWindow() && OpeningCoveragePolicy.overlapsOtherService(candidate, assigned)) return false;
            if (!hours.usesOpeningWindow() && morning != null && affectsMorningCoverage(candidate, morning)) {
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
        validateDemandStartTime(organizationId, assignment);
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
        var rows = driverScheduleService.getDriverSchedulesForDate(organizationId, proposed.getAssignmentDate()).scheduledDrivers();
        var analysis = transportationDemandService.getDemandForDate(organizationId, proposed.getAssignmentDate());
        var hours = wardrobeHoursService.getHours(organizationId, proposed.getAssignmentDate());
        if (hours.usesOpeningWindow()) {
            if (OpeningCoveragePolicy.overlapsOtherService(proposed, assignments))
                throw new IllegalStateException("The same driver cannot cover overlapping assignments. Choose another available driver.");
            var opening = findAssignment(assignments, OpeningCoveragePolicy.openingPeriod(hours));
            if (opening != null && (opening == proposed || assignmentsOverlap(proposed, opening))
                    && (opening.getAssignmentOutcome() == AssignmentOutcome.PENDING || opening.getAssignmentOutcome() == AssignmentOutcome.IN_PROGRESS)
                    && !LateOpeningPolicy.canAssign(opening, rows, assignments, hours.closingTime()))
                throw new IllegalStateException(LateOpeningPolicy.MESSAGE);
            return;
        }
        var openingPeriod = LateOpeningPolicy.openingPeriod(analysis, rows);
        var closing = hours.closingTime();
        for (var opening : assignments) {
            if (opening.getPeriod() == openingPeriod
                    && (opening.getAssignmentOutcome() == AssignmentOutcome.PENDING
                        || opening.getAssignmentOutcome() == AssignmentOutcome.IN_PROGRESS)
                    && (opening == proposed || assignmentsOverlap(proposed, opening))
                    && !LateOpeningPolicy.canAssign(opening, rows, assignments, closing)) {
                throw new IllegalStateException(LateOpeningPolicy.MESSAGE);
            }
        }
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

    private boolean assignmentsOverlap(DriverAssignment first, DriverAssignment second) {
        if (first.getScheduledStartTime() == null || first.getScheduledEndTime() == null
                || second.getScheduledStartTime() == null || second.getScheduledEndTime() == null) return false;
        return first.getScheduledStartTime().isBefore(second.getScheduledEndTime())
                && first.getScheduledEndTime().isAfter(second.getScheduledStartTime());
    }

    private TransportationDemandPeriod upcomingDemand(TransportationDemandPeriod demand, LocalDate date, LocalDateTime now) {
        if (!date.equals(now.toLocalDate())) return demand;
        var next = now.toLocalTime().withSecond(0).withNano(0).plusMinutes(1);
        return next.equals(LocalTime.MIDNIGHT) ? EveningClosingPolicy.afterClosing(demand, next)
                : EveningClosingPolicy.notBefore(demand, next);
    }

    private DriverAssignment createOpeningAssignment(Long organizationId, LocalDate date, ShuttlePeriod period,
            TransportationDemandPeriod demand, List<DriverSchedule> ranked, Map<Long, List<DriverSchedule>> schedules,
            List<DriverSchedule> rows, List<DriverAssignment> assigned, LocalTime closing, ScheduleImport source) {
        DriverAssignment best = null;
        // Cover the most safe demand minutes; workload fairness breaks equal-coverage ties.
        for (var row : ranked) {
            var windows = LateOpeningPolicy.safeWindows(demand, rows, assigned, row.driverId(), closing);
            if (windows.isEmpty()) continue;
            var candidate = DriverAssignment.automatic(date, period, loadDriver(organizationId, row.driverId()), source);
            if (windowPlanner.applyMiddayPlan(candidate, schedules.get(row.driverId()), windows)
                    && (best == null || candidate.getScheduledMinutes() > best.getScheduledMinutes())) best = candidate;
        }
        return best;
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
        return createContinuousPeriodAssignment(organizationId, assignmentDate, period, demandPeriod, fairestOrder,
                schedulesByDriverId, avoidDriverIds, sourceScheduleImport, false);
    }

    private DriverAssignment createContinuousPeriodAssignment(
            Long organizationId, LocalDate assignmentDate, ShuttlePeriod period, TransportationDemandPeriod demandPeriod,
            List<DriverSchedule> fairestOrder, Map<Long, List<DriverSchedule>> schedulesByDriverId,
            Set<Long> avoidDriverIds, ScheduleImport sourceScheduleImport, boolean wholeOpeningWindow) {
        if (demandPeriod == null
                || !demandPeriod.hasDemand()
                || !demandPeriod.hasRecommendedWindow()) {
            return null;
        }

        // A partial work shift must overlap actual demand, not just a quiet gap or end buffer.
        Map<Long, List<DriverSchedule>> demandSchedules = new java.util.HashMap<>();
        schedulesByDriverId.forEach((id, rows) -> demandSchedules.put(id, rows.stream()
                .filter(row -> demandPeriod.getWindows().stream().anyMatch(w -> w.getDemandCount() > 0
                        && row.clipTo(w.getWindowStart(), w.getWindowEnd()) != null)).toList()));
        DriverSchedule driverSchedule = scheduleSelector.chooseForWindow(
                fairestOrder,
                demandSchedules,
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

        LocalTime firstCoveredDemand = wholeOpeningWindow ? demandPeriod.getRecommendedStartTime() : demandPeriod.getWindows().stream()
                .filter(w -> w.getDemandCount() > 0)
                .map(w -> driverSchedule.clipTo(w.getWindowStart(), w.getWindowEnd()))
                .filter(java.util.Objects::nonNull).map(DriverSchedule.DrivingWindow::startTime)
                .min(LocalTime::compareTo).orElseThrow();
        windowPlanner.applyContinuousPlanWithBreak(
                assignment,
                driverSchedule,
                firstCoveredDemand,
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
