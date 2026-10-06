package technology.renatus.routepulse.wardrobe;

import java.time.LocalTime;
import java.util.*;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.service.planning.LateOpeningPolicy;
import technology.renatus.routepulse.scheduling.domain.*;

/** Operational windows are independent of the fixed labels in the raw demand forecast. */
public final class OpeningCoveragePolicy {
    private OpeningCoveragePolicy() {}

    public static ShuttlePeriod openingPeriod(WardrobeHoursService.Hours hours) {
        return hours.openingStartTime().isBefore(LocalTime.NOON) ? ShuttlePeriod.MORNING : ShuttlePeriod.MIDDAY;
    }

    public static TransportationDemandPeriod morning(TransportationDemandAnalysis demand, WardrobeHoursService.Hours hours) {
        return openingPeriod(hours) == ShuttlePeriod.MORNING ? opening(demand, hours)
                : range(List.of(), LocalTime.MIDNIGHT, LocalTime.NOON, "Morning");
    }

    public static TransportationDemandPeriod opening(TransportationDemandAnalysis demand, WardrobeHoursService.Hours hours) {
        var raw = range(demand.getMovementWindows(), hours.openingStartTime(), hours.openingEndTime(), "Opening coverage");
        if (!raw.hasDemand()) return raw;
        // Opening is a three-hour service block, including quiet stretches between arrivals.
        return new TransportationDemandPeriod(raw.getKey(), raw.getTitle(), raw.getSummaryTitle(), raw.getDescription(),
                raw.getIcon(), raw.getTotalLabel(), raw.getPeakLabel(), raw.getWindows(), hours.openingStartTime(),
                hours.openingEndTime(), raw.getTotalCount(), raw.getPeakWindowStart(), raw.getPeakWindowEnd(), raw.getPeakWindowCount());
    }

    public static TransportationDemandPeriod midday(TransportationDemandAnalysis demand, WardrobeHoursService.Hours hours) {
        if (openingPeriod(hours) == ShuttlePeriod.MIDDAY) return opening(demand, hours);
        if (hours.closingTime().equals(LocalTime.MIDNIGHT)) return range(List.of(), LocalTime.NOON, LocalTime.of(17,0), "Optional coverage");
        var start = later(hours.closingTime(), later(LocalTime.NOON, hours.openingEndTime()));
        return range(demand.getMovementWindows(), start, LocalTime.of(17,0), "Optional coverage");
    }

    public static TransportationDemandPeriod evening(TransportationDemandAnalysis demand, WardrobeHoursService.Hours hours) {
        if (hours.closingTime().equals(LocalTime.MIDNIGHT)) return range(List.of(), LocalTime.NOON, LocalTime.of(17,0), "Later service");
        return range(demand.getMovementWindows(), later(hours.closingTime(), later(LocalTime.of(17,0), hours.openingEndTime())),
                LocalTime.of(23,59), "Later service");
    }

    /** Non-overlapping reporting groups retain raw movement demand, including outside service hours. */
    public static Map<String, TransportationDemandPeriod> reportingPeriods(TransportationDemandAnalysis analysis, WardrobeHoursService.Hours hours) {
        var laterStart = later(LocalTime.of(17, 0), hours.openingEndTime());
        return Map.of("morning", morning(analysis, hours),
                "midday", openingPeriod(hours) == ShuttlePeriod.MIDDAY
                        ? range(analysis.getMovementWindows(), hours.openingStartTime(), laterStart, "Opening coverage")
                        : range(analysis.getMovementWindows(), hours.openingEndTime(), laterStart, "Optional coverage"),
                "evening", range(analysis.getMovementWindows(), laterStart, LocalTime.of(23,59), "Later service"),
                "off-hours", range(analysis.getMovementWindows(), LocalTime.MIDNIGHT, hours.openingStartTime(), "Outside service hours"));
    }

    public static boolean conflict(DriverAssignment assignment, TransportationDemandAnalysis demand,
            List<DriverSchedule> roster, List<DriverAssignment> assignments, WardrobeHoursService.Hours hours) {
        var start = assignment.getScheduledStartTime(); var end = assignment.getScheduledEndTime();
        if (start == null || end == null) return true;
        if (overlapsOtherService(assignment, assignments)) return true;
        if (assignment.getPeriod() == openingPeriod(hours)) {
            var opening = opening(demand, hours);
            return !opening.hasDemand() || start.isBefore(opening.getRecommendedStartTime())
                    || end.isAfter(hours.openingEndTime())
                    || !LateOpeningPolicy.canAssign(assignment, roster, assignments, hours.closingTime());
        }
        if (assignment.getPeriod() == ShuttlePeriod.MORNING) return true;
        // Managers may overlap different drivers when a roster member can still cover the counter.
        if (start.isBefore(hours.openingStartTime())
                || !LateOpeningPolicy.canAssign(assignment, roster, assignments, hours.closingTime())) return true;
        var eligible = assignment.getPeriod() == ShuttlePeriod.MIDDAY ? midday(demand, hours)
                : range(demand.getMovementWindows(), LocalTime.of(17,0), LocalTime.of(23,59), "Evening");
        return eligible.hasDemand() && start.isBefore(eligible.getRecommendedStartTime());
    }

    /** Different drivers may overlap; the same person cannot cover two duties at once. */
    public static boolean overlapsOtherService(DriverAssignment proposed, List<DriverAssignment> assignments) {
        return assignments.stream().filter(a -> a != proposed && (a.getId() == null || !a.getId().equals(proposed.getId())))
                .filter(a -> a.getScheduledDriver() != null && proposed.getScheduledDriver() != null
                        && a.getScheduledDriver().getId().equals(proposed.getScheduledDriver().getId()))
                .filter(a -> a.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                        && a.getAssignmentOutcome() != AssignmentOutcome.EXCUSED && a.getAssignmentOutcome() != AssignmentOutcome.MISSED)
                .anyMatch(a -> windows(a).stream().anyMatch(other -> windows(proposed).stream().anyMatch(w ->
                        w.getStartTime().isBefore(other.getEndTime()) && w.getEndTime().isAfter(other.getStartTime()))));
    }

    private static List<technology.renatus.routepulse.assignments.model.PlannedDrivingWindow> windows(DriverAssignment a) {
        if (a.hasPlannedDrivingWindows()) return a.getPlannedDrivingWindows();
        if (a.getScheduledStartTime() == null || a.getScheduledEndTime() == null || !a.getScheduledEndTime().isAfter(a.getScheduledStartTime())) return List.of();
        return List.of(new technology.renatus.routepulse.assignments.model.PlannedDrivingWindow(a.getScheduledStartTime(), a.getScheduledEndTime()));
    }

    public static TransportationDemandPeriod range(List<TransportationDemandWindow> raw, LocalTime start, LocalTime end, String title) {
        var grouped = new TreeMap<LocalTime, int[]>();
        var ends = new HashMap<LocalTime, LocalTime>();
        if (end.isAfter(start)) for (var w : raw) {
            if (w.getDemandCount() <= 0 || !w.getWindowStart().isBefore(end) || !w.getWindowEnd().isAfter(start)) continue;
            var from = later(start, w.getWindowStart()); var to = w.getWindowEnd().isAfter(end) ? end : w.getWindowEnd();
            var counts = grouped.computeIfAbsent(from, ignored -> new int[3]);
            counts[0] += w.getArrivals(); counts[1] += w.getDepartures(); counts[2] += w.getDemandCount();
            ends.merge(from, to, OpeningCoveragePolicy::later);
        }
        var windows = grouped.entrySet().stream().map(e -> new TransportationDemandWindow(e.getKey(), ends.get(e.getKey()),
                e.getValue()[0], e.getValue()[1], e.getValue()[2], null)).toList();
        if (windows.isEmpty()) return TransportationDemandPeriod.empty("coverage", title, title, "", "", "movements", "Peak demand");
        var peak = windows.stream().max(Comparator.comparingInt(TransportationDemandWindow::getDemandCount)).orElseThrow();
        return new TransportationDemandPeriod("coverage", title, title, "", "", "movements", "Peak demand", windows,
                windows.get(0).getWindowStart(), windows.get(windows.size()-1).getWindowEnd(),
                windows.stream().mapToInt(TransportationDemandWindow::getDemandCount).sum(), peak.getWindowStart(), peak.getWindowEnd(), peak.getDemandCount());
    }
    private static LocalTime later(LocalTime a, LocalTime b) { return a.isAfter(b) ? a : b; }
}
