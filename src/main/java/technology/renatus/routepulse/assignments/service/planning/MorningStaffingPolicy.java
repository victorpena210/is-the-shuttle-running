package technology.renatus.routepulse.assignments.service.planning;

import java.time.LocalTime;
import java.util.*;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;

/** Office coverage uses distinct active roster members in the uploaded work schedule. */
public final class MorningStaffingPolicy {
    public static final String MESSAGE = "No driving assignment—wardrobe office coverage required. "
            + "At least two driver-roster staff must be scheduled throughout 9:00 AM–12:00 PM, "
            + "with someone available in wardrobe while the driver is away.";
    private MorningStaffingPolicy() {}

    public static boolean hasMorningStaffing(List<DriverSchedule> schedules, List<DriverAssignment> assignments) {
        return covered(schedules, assignments, null, LocalTime.of(9, 0), LocalTime.NOON, 2);
    }

    public static boolean canAssign(List<DriverSchedule> schedules, List<DriverAssignment> assignments,
            Long driverId, LocalTime start, LocalTime end) {
        return hasMorningStaffing(schedules, assignments)
                && covered(schedules, assignments, driverId, start, end, 1);
    }

    private static boolean covered(List<DriverSchedule> schedules, List<DriverAssignment> assignments,
            Long drivingDriverId, LocalTime start, LocalTime end, int minimum) {
        if (schedules == null || start == null || end == null || !end.isAfter(start)) return false;
        TreeSet<LocalTime> boundaries = new TreeSet<>(List.of(start, end));
        schedules.forEach(s -> { if (s.startTime() != null) boundaries.add(s.startTime());
            if (s.endTime() != null) boundaries.add(s.endTime()); });
        List<DriverAssignment> otherDuties = assignments.stream()
                .filter(a -> a.getPeriod() != ShuttlePeriod.MORNING
                        && a.getAssignmentOutcome() != AssignmentOutcome.CANCELLED
                        && a.getAssignmentOutcome() != AssignmentOutcome.EXCUSED
                        && a.getAssignmentOutcome() != AssignmentOutcome.MISSED).toList();
        otherDuties.forEach(a -> {
            if (a.hasPlannedDrivingWindows()) a.getPlannedDrivingWindows().forEach(w -> {
                boundaries.add(w.getStartTime()); boundaries.add(w.getEndTime());
            });
            else { if (a.getScheduledStartTime() != null) boundaries.add(a.getScheduledStartTime());
                if (a.getScheduledEndTime() != null) boundaries.add(a.getScheduledEndTime()); }
        });
        List<LocalTime> points = boundaries.stream().filter(t -> !t.isBefore(start) && !t.isAfter(end)).toList();
        for (int i = 0; i < points.size() - 1; i++) {
            LocalTime from = points.get(i), to = points.get(i + 1);
            Set<Long> available = new HashSet<>();
            schedules.stream().filter(s -> s.covers(from, to)).forEach(s -> available.add(s.driverId()));
            otherDuties.stream().filter(a -> busy(a, from, to))
                    .forEach(a -> available.remove(a.getScheduledDriver().getId()));
            if (drivingDriverId != null && !available.remove(drivingDriverId)) return false;
            if (available.size() < minimum) return false;
        }
        return true;
    }

    private static boolean busy(DriverAssignment a, LocalTime from, LocalTime to) {
        if (a.hasPlannedDrivingWindows()) return a.getPlannedDrivingWindows().stream()
                .anyMatch(w -> w.getStartTime().isBefore(to) && w.getEndTime().isAfter(from));
        return a.getScheduledStartTime() != null && a.getScheduledEndTime() != null
                && a.getScheduledStartTime().isBefore(to) && a.getScheduledEndTime().isAfter(from);
    }
}
