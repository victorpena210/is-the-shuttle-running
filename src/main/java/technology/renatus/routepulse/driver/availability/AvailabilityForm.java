package technology.renatus.routepulse.driver.availability;

import java.time.*;
import java.util.*;

public class AvailabilityForm {
    private String token;
    private boolean confirmed;
    private List<Row> rows = new ArrayList<>();
    public static class Row {
        private String date = "", start = "", end = "";
        private boolean off;
        public Row() { }
        public Row(ScheduleScreenshotParser.Row row) { date=row.date(); start=row.start(); end=row.end(); off=row.off(); }
        public String getDate() { return date; } public void setDate(String v) { date=v; }
        public String getStart() { return start; } public void setStart(String v) { start=v; }
        public String getEnd() { return end; } public void setEnd(String v) { end=v; }
        public boolean isOff() { return off; } public void setOff(boolean v) { off=v; }
    }
    public String getToken() { return token; } public void setToken(String v) { token=v; }
    public boolean isConfirmed() { return confirmed; } public void setConfirmed(boolean v) { confirmed=v; }
    public List<Row> getRows() { return rows; } public void setRows(List<Row> v) { rows=v; }
    public Map<LocalDate,List<AvailabilityWindows.Window>> validated(LocalDate today) {
        if (!confirmed) throw new IllegalArgumentException("Confirm that you checked every date and time.");
        if (rows == null || rows.size() > 60) throw new IllegalArgumentException("Use at most 60 rows.");
        Map<LocalDate,List<AvailabilityWindows.Window>> result = new TreeMap<>(); Set<LocalDate> off = new HashSet<>();
        for (var row : rows) {
            if (row == null) throw new IllegalArgumentException("A schedule row is incomplete.");
            if (blank(row.date) && blank(row.start) && blank(row.end) && !row.off) continue;
            LocalDate date;
            try { date = LocalDate.parse(row.date); }
            catch (Exception ex) { throw new IllegalArgumentException("Enter the full date for every shift, including the year."); }
            if (date.isBefore(today) || date.isAfter(today.plusDays(366)))
                throw new IllegalArgumentException("Updates must be for today or the next 366 days.");
            if (off.contains(date) || row.off && result.containsKey(date))
                throw new IllegalArgumentException("A date cannot have both a shift and an off entry, or repeated off entries.");
            var windows = result.computeIfAbsent(date, ignored -> new ArrayList<>());
            if (row.off) { off.add(date); continue; }
            try { windows.add(new AvailabilityWindows.Window(LocalTime.parse(row.start),LocalTime.parse(row.end))); }
            catch (Exception ex) { throw new IllegalArgumentException("Enter both start and end times for " + date + "."); }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Add at least one date to update.");
        result.values().forEach(AvailabilityWindows::validate);
        // Prevent consecutive entries from overlapping across midnight.
        LocalDateTime previousEnd = null;
        for (var entry : result.entrySet()) for (var w : entry.getValue().stream().sorted(Comparator.comparing(AvailabilityWindows.Window::start)).toList()) {
            var start = entry.getKey().atTime(w.start()); var end = entry.getKey().atTime(w.end());
            if (!end.isAfter(start)) end = end.plusDays(1);
            if (previousEnd != null && start.isBefore(previousEnd)) throw new IllegalArgumentException("Your shifts overlap across midnight.");
            previousEnd = end;
        }
        return result;
    }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
}
