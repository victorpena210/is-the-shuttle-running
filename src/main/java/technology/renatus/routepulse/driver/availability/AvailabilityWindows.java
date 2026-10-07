package technology.renatus.routepulse.driver.availability;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;

/** Small, deterministic format for audited work hours. Empty means explicitly off. */
public final class AvailabilityWindows {
    private AvailabilityWindows() { }
    public record Window(LocalTime start, LocalTime end) { }
    public static String encode(List<Window> windows) {
        return windows.stream().sorted(Comparator.comparing(Window::start))
                .map(w -> w.start() + "-" + w.end()).collect(Collectors.joining(","));
    }
    public static List<Window> decode(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(s -> {
            String[] times = s.split("-");
            return new Window(LocalTime.parse(times[0]), LocalTime.parse(times[1]));
        }).toList();
    }
    public static String label(String value) {
        var fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        var windows = decode(value);
        return windows.isEmpty() ? "Off / unavailable" : windows.stream()
                .map(w -> w.start().format(fmt) + "–" + w.end().format(fmt)
                        + (!w.end().isAfter(w.start()) ? " (next day)" : ""))
                .collect(Collectors.joining(", "));
    }
    public static void validate(List<Window> windows) {
        if (windows.size() > 4) throw new IllegalArgumentException("Use at most four shifts per date.");
        int previousEnd = -1;
        for (var w : windows.stream().sorted(Comparator.comparing(Window::start)).toList()) {
            if (w.start() == null || w.end() == null || w.start().equals(w.end())
                    || w.start().getSecond() != 0 || w.end().getSecond() != 0
                    || w.start().getNano() != 0 || w.end().getNano() != 0)
                throw new IllegalArgumentException("Enter different start and end times in whole minutes.");
            int start = w.start().toSecondOfDay() / 60;
            int end = w.end().toSecondOfDay() / 60;
            if (end <= start) end += 1440;
            if (end - start > 18 * 60) throw new IllegalArgumentException("A work shift cannot exceed 18 hours. Check AM/PM.");
            if (start < previousEnd) throw new IllegalArgumentException("Work shifts on the same date cannot overlap.");
            previousEnd = end;
        }
    }
    public static boolean covers(List<DriverSchedule> shifts, LocalTime start, LocalTime end) {
        if (start == null || end == null || start.equals(end)) return false;
        int cursor = start.toSecondOfDay() / 60, target = end.toSecondOfDay() / 60;
        if (target <= cursor) target += 1440;
        for (var s : shifts.stream().sorted(Comparator.comparing(DriverSchedule::startTime)).toList()) {
            int from = s.startTime().toSecondOfDay()/60, to = s.endTime().toSecondOfDay()/60;
            if (to <= from) to += 1440;
            if (from > cursor) break;
            cursor = Math.max(cursor, to);
            if (cursor >= target) return true;
        }
        return false;
    }
}
