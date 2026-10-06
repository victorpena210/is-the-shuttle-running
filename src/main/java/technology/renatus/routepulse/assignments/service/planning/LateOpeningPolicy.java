package technology.renatus.routepulse.assignments.service.planning;

import java.time.LocalTime;
import java.util.*;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.*;
import technology.renatus.routepulse.scheduling.domain.*;
import technology.renatus.routepulse.wardrobe.EveningClosingPolicy;

/** The first demand period on an afternoon-start roster is opening coverage, regardless of weekday. */
public final class LateOpeningPolicy {
    public static final String MESSAGE = "Opening driving requires another scheduled driver-roster member "
            + "at the wardrobe counter until closing. With only one available, that person stays at the counter.";
    private LateOpeningPolicy() {}

    public static ShuttlePeriod openingPeriod(TransportationDemandAnalysis demand, List<DriverSchedule> schedules) {
        if (demand.getMorning().hasDemand() || schedules.isEmpty()
                || firstRosterStart(schedules).isBefore(LocalTime.NOON)) return null;
        var start = firstRosterStart(schedules);
        if (demand.getMidday().getWindows().stream().anyMatch(w -> w.getDemandCount() > 0 && w.getWindowEnd().isAfter(start)))
            return ShuttlePeriod.MIDDAY;
        return demand.getEvening().getWindows().stream().anyMatch(w -> w.getDemandCount() > 0 && w.getWindowEnd().isAfter(start))
                ? ShuttlePeriod.AFTERNOON : null;
    }

    public static TransportationDemandPeriod drivingDemand(TransportationDemandAnalysis analysis,
            List<DriverSchedule> schedules, ShuttlePeriod period, LocalTime closing) {
        var raw = period == ShuttlePeriod.MIDDAY ? analysis.getMidday() : analysis.getEvening();
        return openingPeriod(analysis, schedules) == period
                ? EveningClosingPolicy.notBefore(raw, firstRosterStart(schedules))
                : EveningClosingPolicy.afterClosing(raw, closing);
    }

    /** Only roster members supplied by DriverScheduleService count; duplicate work rows are one person. */
    public static List<TransportationDemandWindow> safeWindows(TransportationDemandPeriod demand,
            List<DriverSchedule> schedules, List<DriverAssignment> duties, Long driverId, LocalTime closing) {
        List<TransportationDemandWindow> result = new ArrayList<>();
        for (var window : demand.getWindows()) {
            if (window.getDemandCount() <= 0) continue;
            var points = boundaries(schedules, duties, window.getWindowStart(), window.getWindowEnd(), closing);
            for (int i = 0; i < points.size() - 1; i++) {
                var start = points.get(i); var end = points.get(i + 1);
                if (available(schedules, duties, driverId, start, end, closing)) {
                    result.add(new TransportationDemandWindow(start, end, window.getArrivals(), window.getDepartures(),
                            window.getDemandCount(), window.getPriority()));
                }
            }
        }
        return List.copyOf(result);
    }

    public static boolean canAssign(DriverAssignment assignment, List<DriverSchedule> schedules,
            List<DriverAssignment> assignments, LocalTime closing) {
        if (assignment.getScheduledDriver() == null) return false;
        var windows = windows(assignment);
        if (windows.isEmpty()) return false;
        var others = assignments.stream().filter(a -> !sameAssignment(a, assignment)).toList();
        for (var window : windows) {
            var points = boundaries(schedules, others, window.getStartTime(), window.getEndTime(), closing);
            for (int i = 0; i < points.size() - 1; i++) {
                if (!available(schedules, others, assignment.getScheduledDriver().getId(),
                        points.get(i), points.get(i + 1), closing)) return false;
            }
        }
        return true;
    }

    public static boolean hasSafeDemand(TransportationDemandPeriod demand, List<DriverSchedule> schedules,
            List<DriverAssignment> assignments, ShuttlePeriod period, LocalTime closing) {
        // Evaluate whether this period can be staffed without counting its own assignment as another duty.
        var others = assignments.stream().filter(a -> a.getPeriod() != period).toList();
        return schedules.stream().map(DriverSchedule::driverId).distinct()
                .anyMatch(id -> !safeWindows(demand, schedules, others, id, closing).isEmpty());
    }

    private static LocalTime firstRosterStart(List<DriverSchedule> schedules) {
        return schedules.stream().map(DriverSchedule::startTime).min(LocalTime::compareTo).orElseThrow();
    }

    private static List<LocalTime> boundaries(List<DriverSchedule> schedules, List<DriverAssignment> duties,
            LocalTime start, LocalTime end, LocalTime closing) {
        var points = new TreeSet<LocalTime>(List.of(start, end, closing));
        schedules.forEach(s -> { points.add(s.startTime()); points.add(s.endTime()); });
        duties.stream().filter(LateOpeningPolicy::active).flatMap(a -> windows(a).stream())
                .forEach(w -> { points.add(w.getStartTime()); points.add(w.getEndTime()); });
        return points.stream().filter(t -> !t.isBefore(start) && !t.isAfter(end)).toList();
    }

    private static boolean available(List<DriverSchedule> schedules, List<DriverAssignment> duties,
            Long driverId, LocalTime start, LocalTime end, LocalTime closing) {
        Set<Long> available = new HashSet<>();
        schedules.stream().filter(s -> s.covers(start, end)).forEach(s -> available.add(s.driverId()));
        duties.stream().filter(LateOpeningPolicy::active)
                .filter(a -> windows(a).stream().anyMatch(w -> w.getStartTime().isBefore(end) && w.getEndTime().isAfter(start)))
                .forEach(a -> available.remove(a.getScheduledDriver().getId()));
        if (!available.remove(driverId)) return false;
        return (!closing.equals(LocalTime.MIDNIGHT) && !start.isBefore(closing)) || !available.isEmpty();
    }

    private static boolean active(DriverAssignment a) {
        return a.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                && a.getAssignmentOutcome() != AssignmentOutcome.EXCUSED
                && a.getAssignmentOutcome() != AssignmentOutcome.MISSED;
    }

    private static boolean sameAssignment(DriverAssignment a, DriverAssignment b) {
        return a == b || a.getId() != null && a.getId().equals(b.getId());
    }

    private static List<PlannedDrivingWindow> windows(DriverAssignment a) {
        if (a.hasPlannedDrivingWindows()) return a.getPlannedDrivingWindows();
        if (a.getScheduledStartTime() == null || a.getScheduledEndTime() == null
                || !a.getScheduledEndTime().isAfter(a.getScheduledStartTime())) return List.of();
        return List.of(new PlannedDrivingWindow(a.getScheduledStartTime(), a.getScheduledEndTime()));
    }
}
