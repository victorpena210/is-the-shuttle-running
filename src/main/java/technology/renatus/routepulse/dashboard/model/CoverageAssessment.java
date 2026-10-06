package technology.renatus.routepulse.dashboard.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.attention.service.AttentionSupport;

/** One interval calculation shared by dashboard badges, totals, and attention alerts. */
public record CoverageAssessment(String state, String label, List<String> gaps,
        List<String> explanations, boolean fullyCovered) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    public static CoverageAssessment assess(DashboardPlan plan, ShuttlePeriod period) {
        var demand = AttentionSupport.demandPeriod(plan, period);
        List<Interval> required = demand.getWindows().stream().filter(w -> w.getDemandCount() > 0)
                .map(w -> interval(plan.selectedDate(), plan.selectedDate(), w.getWindowStart(), w.getWindowEnd()))
                .filter(Objects::nonNull).toList();
        if (required.isEmpty() && demand.hasDemand() && demand.hasRecommendedWindow()) {
            required = List.of(interval(plan.selectedDate(), plan.selectedDate(),
                    demand.getRecommendedStartTime(), demand.getRecommendedEndTime()));
        }
        required = merge(required);
        List<Interval> covered = new ArrayList<>();
        boolean review = (period == ShuttlePeriod.MORNING && plan.morningStaffingBlocked())
                || (period == plan.lateOpeningPeriod() && plan.openingStaffingBlocked());
        List<String> reasons = new ArrayList<>();
        var day = plan.operatingDay();
        reasons.add(day == null || day.openingTime() == null ? "Park opening time is unverified."
                : "Park opens at " + day.openingTime().format(TIME) + ".");
        reasons.add("Wardrobe hours: " + plan.wardrobeHours().openingStartTime().format(TIME)
                + "–" + plan.wardrobeHours().closingTime().format(TIME) + " (" + plan.wardrobeHours().label() + ").");
        reasons.add("Demand intervals: " + (required.isEmpty() ? "none" : labels(required)) + ".");
        var candidates = plan.driverSchedules().stream().filter(s -> demand.getWindows().stream()
                .anyMatch(w -> w.getDemandCount() > 0 && s.overlaps(w.getWindowStart(), w.getWindowEnd())))
                .map(s -> s.driverName() + " (" + s.startTime().format(TIME) + "–" + s.endTime().format(TIME) + ")")
                .distinct().collect(Collectors.joining(", "));
        reasons.add("Uploaded work shifts overlapping demand: " + (candidates.isEmpty() ? "none" : candidates) + ".");

        for (DriverAssignment a : plan.driverAssignments()) {
            if (!usable(a)) continue;
            var windows = windows(plan.selectedDate(), a);
            boolean relevant = a.getPeriod() == period || windows.stream().anyMatch(w -> requiredOverlap(demand, plan, w));
            if (!relevant) continue;
            boolean conflict = hasDutyConflict(plan, a, windows) || lacksCounterCoverage(plan, a, windows);
            boolean planningConflict = plan.wardrobeHours().usesOpeningWindow()
                    ? technology.renatus.routepulse.wardrobe.OpeningCoveragePolicy.conflict(a, plan.transportationDemand(),
                            plan.driverSchedules(), plan.driverAssignments(), plan.wardrobeHours())
                    : technology.renatus.routepulse.assignments.service.AssignmentPlanningService.hasDrivingConflict(a,
                            plan.transportationDemand(), plan.driverSchedules(), plan.driverAssignments(), plan.wardrobeHours().closingTime());
            if (planningConflict) review = true;
            if (conflict) review = true;
            var roster = plan.driverSchedules().stream().filter(s -> Objects.equals(s.driverId(), a.getScheduledDriver().getId()))
                    .map(s -> interval(plan.selectedDate(), s.shiftDate(), s.startTime(), s.endTime()))
                    .filter(Objects::nonNull).toList();
            var available = intersections(windows, roster);
            if (!subtract(windows, merge(available)).isEmpty()) review = true;
            if (!conflict) covered.addAll(available);
            reasons.add(a.getScheduledDriver().getName() + ": assigned " + labels(windows)
                    + "; uploaded work shift " + (roster.isEmpty() ? "not found" : labels(roster)) + ".");
            if (a.getAssignmentSource() != null) reasons.add("Selection: " + a.getAssignmentSource()
                    + (a.getChangeReason() == null ? "." : " — " + a.getChangeReason()));
            if (a.getAssignmentSource() == technology.renatus.routepulse.assignments.domain.AssignmentSource.AUTO) {
                reasons.add("Automatic selection uses availability, make-up priority, actual driving time, planned workload, and rotating ties. Historical candidate scores were not recorded.");
            }
        }
        if (plan.scheduleReconciliations().stream().anyMatch(r -> r.requiresAction()
                && plan.driverAssignments().stream().anyMatch(a -> a.getPeriod() == period && Objects.equals(a.getId(), r.assignmentId())))) review = true;
        reasons.add(review ? "Counter staffing or a schedule/assignment conflict needs review."
                : "No counter-staffing or uploaded-shift conflict was found for the assessed coverage.");
        var gaps = subtract(required, merge(covered));
        long requiredMinutes = required.stream().mapToLong(w -> w.end - w.start).sum();
        long missingMinutes = gaps.stream().mapToLong(w -> w.end - w.start).sum();
        boolean assigned = plan.driverAssignments().stream().anyMatch(a -> a.getPeriod() == period && usable(a));
        String state = review ? "REVIEW" : required.isEmpty() ? (assigned ? "ASSIGNED" : "NONE")
                : gaps.isEmpty() ? "COVERED" : missingMinutes < requiredMinutes ? "PARTIAL" : assigned ? "ASSIGNED" : "UNASSIGNED";
        String label = switch (state) {
            case "REVIEW" -> "Needs review";
            case "COVERED" -> "Covered";
            case "PARTIAL" -> "Partially covered";
            case "ASSIGNED" -> "Assigned";
            case "UNASSIGNED" -> "Needs driver";
            default -> "No demand";
        };
        reasons.add(gaps.isEmpty() ? "No uncovered demand intervals remain."
                : "Uncovered demand: " + labels(gaps) + ".");
        return new CoverageAssessment(state, label, gaps.stream().map(Interval::label).toList(),
                List.copyOf(reasons), !required.isEmpty() && gaps.isEmpty() && !review);
    }

    private static boolean requiredOverlap(technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod demand,
            DashboardPlan plan, Interval window) {
        return demand.getWindows().stream().filter(w -> w.getDemandCount() > 0)
                .map(w -> interval(plan.selectedDate(), plan.selectedDate(), w.getWindowStart(), w.getWindowEnd()))
                .filter(Objects::nonNull).anyMatch(w -> window.start < w.end && w.start < window.end);
    }

    private static boolean usable(DriverAssignment a) {
        return a.getScheduledDriver() != null && a.getScheduledDriver().isActive()
                && a.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                && a.getAssignmentOutcome() != AssignmentOutcome.EXCUSED
                && a.getAssignmentOutcome() != AssignmentOutcome.MISSED;
    }

    private static boolean hasDutyConflict(DashboardPlan plan, DriverAssignment assignment, List<Interval> windows) {
        return plan.driverAssignments().stream().filter(CoverageAssessment::usable)
                .filter(a -> a != assignment && (a.getId() == null || !a.getId().equals(assignment.getId())))
                .filter(a -> Objects.equals(a.getScheduledDriver().getId(), assignment.getScheduledDriver().getId()))
                .anyMatch(a -> !intersections(windows, windows(plan.selectedDate(), a)).isEmpty());
    }

    private static boolean lacksCounterCoverage(DashboardPlan plan, DriverAssignment assignment, List<Interval> windows) {
        int dayOffset = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(plan.selectedDate(), assignment.getAssignmentDate())) * 1440;
        int closing = plan.wardrobeHours().closingTime().equals(LocalTime.MIDNIGHT) ? 1440 : plan.wardrobeHours().closingTime().toSecondOfDay() / 60;
        var counterRequired = intersections(windows, List.of(new Interval(dayOffset, dayOffset + closing)));
        if (counterRequired.isEmpty()) return false;
        List<Interval> counterAvailable = new ArrayList<>();
        for (var s : plan.driverSchedules()) {
            if (Objects.equals(s.driverId(), assignment.getScheduledDriver().getId())) continue;
            var shift = interval(plan.selectedDate(), s.shiftDate(), s.startTime(), s.endTime());
            if (shift == null) continue;
            var duties = plan.driverAssignments().stream().filter(CoverageAssessment::usable)
                    .filter(a -> Objects.equals(a.getScheduledDriver().getId(), s.driverId()))
                    .flatMap(a -> windows(plan.selectedDate(), a).stream()).toList();
            counterAvailable.addAll(subtract(List.of(shift), merge(duties)));
        }
        return !subtract(counterRequired, merge(counterAvailable)).isEmpty();
    }

    private static List<Interval> windows(LocalDate base, DriverAssignment a) {
        if (a.hasPlannedDrivingWindows()) return a.getPlannedDrivingWindows().stream()
                .map(w -> interval(base, a.getAssignmentDate(), w.getStartTime(), w.getEndTime())).filter(Objects::nonNull).toList();
        var window = interval(base, a.getAssignmentDate(), a.getScheduledStartTime(), a.getScheduledEndTime());
        return window == null ? List.of() : List.of(window);
    }

    private static Interval interval(LocalDate base, LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null || start.equals(end)) return null;
        int offset = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(base, date)) * 1440;
        int from = offset + start.toSecondOfDay() / 60;
        int to = offset + end.toSecondOfDay() / 60;
        if (to <= from) to += 1440;
        return new Interval(from, to);
    }

    private static List<Interval> intersections(List<Interval> left, List<Interval> right) {
        List<Interval> result = new ArrayList<>();
        for (var a : left) for (var b : right) {
            int start = Math.max(a.start, b.start), end = Math.min(a.end, b.end);
            if (end > start) result.add(new Interval(start, end));
        }
        return result;
    }

    private static List<Interval> merge(List<Interval> source) {
        List<Interval> result = new ArrayList<>();
        for (var w : source.stream().sorted(Comparator.comparingInt(Interval::start)).toList()) {
            if (result.isEmpty() || result.get(result.size() - 1).end < w.start) result.add(w);
            else { var last = result.remove(result.size() - 1); result.add(new Interval(last.start, Math.max(last.end, w.end))); }
        }
        return result;
    }

    private static List<Interval> subtract(List<Interval> required, List<Interval> covered) {
        List<Interval> result = new ArrayList<>();
        for (var w : required) {
            int cursor = w.start;
            for (var c : covered) {
                if (c.end <= cursor || c.start >= w.end) continue;
                if (c.start > cursor) result.add(new Interval(cursor, Math.min(c.start, w.end)));
                cursor = Math.min(w.end, Math.max(cursor, c.end));
            }
            if (cursor < w.end) result.add(new Interval(cursor, w.end));
        }
        return merge(result);
    }

    private static String labels(List<Interval> intervals) {
        return intervals.isEmpty() ? "no usable window" : intervals.stream().map(Interval::label).collect(Collectors.joining(", "));
    }
    private record Interval(int start, int end) {
        String label() { return time(start) + "–" + time(end); }
        private static String time(int minute) {
            return LocalTime.ofSecondOfDay(Math.floorMod(minute, 1440) * 60L).format(TIME)
                    + (minute >= 1440 ? " (next day)" : minute < 0 ? " (previous day)" : "");
        }
    }
}
