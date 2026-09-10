package technology.renatus.routepulse.reporting.pilot.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.reporting.pilot.model.DriverFairnessRow;
import technology.renatus.routepulse.reporting.pilot.model.PickupWindowStat;
import technology.renatus.routepulse.reporting.pilot.model.PilotAnalyticsReport;
import technology.renatus.routepulse.scheduling.domain.PickupWindow;
import technology.renatus.routepulse.scheduling.planning.PickupPlanningService;
import technology.renatus.routepulse.scheduling.planning.common.DemandPriorityClassifier;
import technology.renatus.routepulse.scheduling.retention.repository.HistoricalDemandWindowRepository;

/** Builds organization-scoped weekly analytics from RoutePulse operational records. */
@Service
@Transactional(readOnly = true)
public class PilotAnalyticsService {

    private final DriverAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final PassengerRideConfirmationRepository passengerConfirmationRepository;
    private final PickupPlanningService pickupPlanningService;
    private final HistoricalDemandWindowRepository historicalDemandWindowRepository;
    private final DemandPriorityClassifier demandPriorityClassifier;
    private final ZoneId operationsZone;

    public PilotAnalyticsService(
            DriverAssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            PassengerRideConfirmationRepository passengerConfirmationRepository,
            PickupPlanningService pickupPlanningService,
            HistoricalDemandWindowRepository historicalDemandWindowRepository,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone) {

        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.passengerConfirmationRepository = passengerConfirmationRepository;
        this.pickupPlanningService = pickupPlanningService;
        this.historicalDemandWindowRepository = historicalDemandWindowRepository;
        this.demandPriorityClassifier = new DemandPriorityClassifier();
        this.operationsZone = ZoneId.of(operationsZone);
    }

    public PilotAnalyticsReport build(
            Long organizationId,
            LocalDate requestedWeek) {

        if (organizationId == null) {
            throw new IllegalArgumentException("Organization is required.");
        }

        LocalDate today = LocalDate.now(operationsZone);
        LocalDate weekStart = normalizeWeekStart(
                requestedWeek == null ? today : requestedWeek
        );
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDateTime now = LocalDateTime.now(operationsZone);

        List<DriverAssignment> assignments = assignmentRepository
                .findAllByOrganization_IdAndAssignmentDateBetween(
                        organizationId,
                        weekStart,
                        weekEnd
                );

        long plannedMinutes = assignments.stream()
                .mapToLong(DriverAssignment::getScheduledMinutes)
                .sum();

        long actualMinutes = assignments.stream()
                .flatMap(assignment -> assignment.getDrivingSessions().stream())
                .mapToLong(session -> actualMinutes(session, now))
                .sum();

        int totalAssignments = assignments.size();

        int automaticallyPlannedAssignments = (int) assignments.stream()
                .filter(assignment -> assignment.getOriginallySuggestedDriver() != null)
                .count();

        double automaticCoveragePercent = percentage(
                automaticallyPlannedAssignments,
                totalAssignments
        );

        int managerOverrides = (int) assignments.stream()
                .filter(assignment -> assignment.getAssignmentSource() == AssignmentSource.OVERRIDE)
                .count();

        int missedAssignments = (int) assignments.stream()
                .filter(assignment -> isMissed(assignment, now))
                .count();

        int fillInAssignments = (int) assignments.stream()
                .filter(this::hasFillInCoverage)
                .count();

        long passengerConfirmations = passengerConfirmationRepository
                .countByOrganization_IdAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                        organizationId,
                        weekStart.atStartOfDay(),
                        weekEnd.plusDays(1).atStartOfDay()
                );

        double averageStartDelayMinutes = averageStartDelayMinutes(assignments);

        int scheduleChangesHandled = (int) assignments.stream()
                .filter(this::hasHandledScheduleChange)
                .count();

        List<DriverFairnessRow> fairnessRows = buildFairnessRows(
                organizationId,
                assignments,
                now
        );

        double fairnessSpreadHours = fairnessSpreadHours(fairnessRows);

        List<PickupWindowStat> busiestPickupWindows = busiestPickupWindows(
                organizationId,
                weekStart,
                weekEnd
        );

        return new PilotAnalyticsReport(
                weekStart,
                weekEnd,
                !today.isBefore(weekStart) && !today.isAfter(weekEnd),
                plannedMinutes,
                actualMinutes,
                totalAssignments,
                automaticallyPlannedAssignments,
                automaticCoveragePercent,
                managerOverrides,
                missedAssignments,
                fillInAssignments,
                passengerConfirmations,
                averageStartDelayMinutes,
                scheduleChangesHandled,
                fairnessSpreadHours,
                fairnessRows.size(),
                fairnessRows,
                busiestPickupWindows,
                buildExecutiveSummary(
                        totalAssignments,
                        automaticCoveragePercent,
                        scheduleChangesHandled,
                        managerOverrides,
                        fillInAssignments,
                        missedAssignments
                )
        );
    }

    public LocalDate currentWeekStart() {
        return normalizeWeekStart(LocalDate.now(operationsZone));
    }

    private LocalDate normalizeWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private long actualMinutes(
            DrivingSession session,
            LocalDateTime now) {

        LocalDateTime end = session.getEndedAt();
        if (end == null) {
            if (session.getStartedAt().toLocalDate().equals(now.toLocalDate())) {
                end = now;
            } else if (session.getAssignment().getScheduledEndTime() != null) {
                end = LocalDateTime.of(
                        session.getAssignment().getAssignmentDate(),
                        session.getAssignment().getScheduledEndTime()
                );
            } else {
                end = session.getStartedAt();
            }
        }

        if (end.isBefore(session.getStartedAt())) {
            return 0;
        }

        return Math.max(
                0,
                Duration.between(session.getStartedAt(), end).toMinutes()
        );
    }

    private boolean isMissed(
            DriverAssignment assignment,
            LocalDateTime now) {

        if (assignment.getAssignmentOutcome() == AssignmentOutcome.MISSED) {
            return true;
        }

        if (assignment.getAssignmentOutcome() == AssignmentOutcome.CANCELLED
                || assignment.getAssignmentOutcome() == AssignmentOutcome.EXCUSED
                || !assignment.getDrivingSessions().isEmpty()
                || assignment.getScheduledEndTime() == null) {
            return false;
        }

        LocalDateTime scheduledEnd = LocalDateTime.of(
                assignment.getAssignmentDate(),
                assignment.getScheduledEndTime()
        );

        return scheduledEnd.isBefore(now);
    }

    private boolean hasFillInCoverage(DriverAssignment assignment) {
        Long scheduledDriverId = assignment.getScheduledDriver().getId();

        return assignment.getDrivingSessions().stream()
                .anyMatch(session -> !Objects.equals(
                        scheduledDriverId,
                        session.getDriver().getId()
                ));
    }

    private boolean hasHandledScheduleChange(DriverAssignment assignment) {
        if (assignment.getScheduleReviewedAt() == null
                || assignment.getReviewedScheduleImport() == null) {
            return false;
        }

        if (assignment.getSourceScheduleImport() == null) {
            return true;
        }

        return !Objects.equals(
                assignment.getSourceScheduleImport().getId(),
                assignment.getReviewedScheduleImport().getId()
        );
    }

    private double averageStartDelayMinutes(
            List<DriverAssignment> assignments) {

        long totalDelayMinutes = 0;
        int startedAssignments = 0;

        for (DriverAssignment assignment : assignments) {
            if (assignment.getScheduledStartTime() == null
                    || assignment.getDrivingSessions().isEmpty()) {
                continue;
            }

            LocalDateTime firstStart = assignment.getDrivingSessions().stream()
                    .map(DrivingSession::getStartedAt)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            if (firstStart == null) {
                continue;
            }

            LocalDateTime scheduledStart = LocalDateTime.of(
                    assignment.getAssignmentDate(),
                    assignment.getScheduledStartTime()
            );

            long delay = Math.max(
                    0,
                    Duration.between(scheduledStart, firstStart).toMinutes()
            );

            totalDelayMinutes += delay;
            startedAssignments++;
        }

        if (startedAssignments == 0) {
            return 0.0;
        }

        return totalDelayMinutes / (double) startedAssignments;
    }

    private List<DriverFairnessRow> buildFairnessRows(
            Long organizationId,
            List<DriverAssignment> assignments,
            LocalDateTime now) {

        Map<Long, MutableFairness> byDriver = new LinkedHashMap<>();

        for (Driver driver : driverRepository
                .findAllByOrganization_IdOrderByNameAsc(organizationId)) {
            byDriver.put(driver.getId(), new MutableFairness(driver));
        }

        for (DriverAssignment assignment : assignments) {
            Driver scheduledDriver = assignment.getScheduledDriver();
            MutableFairness scheduled = byDriver.computeIfAbsent(
                    scheduledDriver.getId(),
                    ignored -> new MutableFairness(scheduledDriver)
            );
            scheduled.scheduledMinutes += assignment.getScheduledMinutes();
            scheduled.scheduledAssignments++;

            Map<Long, Boolean> drivenThisAssignment = new LinkedHashMap<>();
            for (DrivingSession session : assignment.getDrivingSessions()) {
                Driver actualDriver = session.getDriver();
                MutableFairness actual = byDriver.computeIfAbsent(
                        actualDriver.getId(),
                        ignored -> new MutableFairness(actualDriver)
                );
                actual.actualMinutes += actualMinutes(session, now);
                drivenThisAssignment.put(actualDriver.getId(), Boolean.TRUE);
            }

            drivenThisAssignment.keySet().forEach(driverId ->
                    byDriver.get(driverId).assignmentsDriven++
            );
        }

        return byDriver.values().stream()
                .filter(row -> row.scheduledMinutes > 0 || row.actualMinutes > 0)
                .map(MutableFairness::toRow)
                .sorted(
                        Comparator.comparingLong(DriverFairnessRow::actualMinutes)
                                .reversed()
                                .thenComparing(DriverFairnessRow::driverName)
                )
                .toList();
    }

    private double fairnessSpreadHours(List<DriverFairnessRow> rows) {
        if (rows.size() < 2) {
            return 0.0;
        }

        long minimum = rows.stream()
                .mapToLong(DriverFairnessRow::actualMinutes)
                .min()
                .orElse(0);
        long maximum = rows.stream()
                .mapToLong(DriverFairnessRow::actualMinutes)
                .max()
                .orElse(0);

        return (maximum - minimum) / 60.0;
    }

    private List<PickupWindowStat> busiestPickupWindows(
            Long organizationId,
            LocalDate weekStart,
            LocalDate weekEnd) {

        Map<PickupWindowKey, Integer> departuresByWindow = new LinkedHashMap<>();

        for (LocalDate date = weekStart;
                !date.isAfter(weekEnd);
                date = date.plusDays(1)) {

            List<PickupWindow> windows = pickupPlanningService
                    .getPickupWindowsForDate(organizationId, date);

            if (windows.isEmpty()) {
                windows = historicalDemandWindowRepository
                        .findAllBySnapshot_Organization_IdAndSnapshot_OperatingDateOrderByWindowStartAsc(
                                organizationId,
                                date
                        )
                        .stream()
                        .filter(window -> window.getDepartures() > 0)
                        .map(window -> new PickupWindow(
                                window.getWindowStart(),
                                window.getWindowEnd(),
                                window.getDepartures(),
                                demandPriorityClassifier.classify(
                                        window.getDepartures()
                                )
                        ))
                        .toList();
            }

            for (PickupWindow window : windows) {
                if (window.getTeamMemberCount() <= 0) {
                    continue;
                }

                PickupWindowKey key = new PickupWindowKey(
                        window.getWindowStart(),
                        window.getWindowEnd()
                );

                departuresByWindow.merge(
                        key,
                        window.getTeamMemberCount(),
                        Integer::sum
                );
            }
        }

        List<PickupWindowStat> results = new ArrayList<>();
        departuresByWindow.forEach((key, departures) ->
                results.add(new PickupWindowStat(
                        key.start(),
                        key.end(),
                        departures
                ))
        );

        return results.stream()
                .sorted(
                        Comparator.comparingInt(PickupWindowStat::scheduledDepartures)
                                .reversed()
                                .thenComparing(PickupWindowStat::windowStart)
                )
                .limit(5)
                .toList();
    }

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return (numerator * 100.0) / denominator;
    }

    private String buildExecutiveSummary(
            int totalAssignments,
            double automaticCoveragePercent,
            int scheduleChangesHandled,
            int managerOverrides,
            int fillInAssignments,
            int missedAssignments) {

        if (totalAssignments == 0) {
            return "No shuttle assignments were planned for this reporting week yet.";
        }

        return "RoutePulse automatically planned "
                + Math.round(automaticCoveragePercent)
                + "% of "
                + totalAssignments
                + " shuttle assignments and handled "
                + scheduleChangesHandled
                + " schedule change"
                + plural(scheduleChangesHandled)
                + " before driving started. Managers used "
                + managerOverrides
                + " override"
                + plural(managerOverrides)
                + ", "
                + fillInAssignments
                + (fillInAssignments == 1
                        ? " fill-in coverage event was captured, and "
                        : " fill-in coverage events were captured, and ")
                + missedAssignments
                + (missedAssignments == 1
                        ? " assignment was missed."
                        : " assignments were missed.");
    }

    private String plural(int count) {
        return count == 1 ? "" : "s";
    }

    private record PickupWindowKey(LocalTime start, LocalTime end) {
    }

    private static final class MutableFairness {
        private final Driver driver;
        private long scheduledMinutes;
        private long actualMinutes;
        private int scheduledAssignments;
        private int assignmentsDriven;

        private MutableFairness(Driver driver) {
            this.driver = driver;
        }

        private DriverFairnessRow toRow() {
            return new DriverFairnessRow(
                    driver.getId(),
                    driver.getName(),
                    scheduledMinutes,
                    actualMinutes,
                    scheduledAssignments,
                    assignmentsDriven
            );
        }
    }
}
