package technology.renatus.routepulse.scheduling.importing;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** UKG Employee Schedule - Weekly: names and continuation rows beneath seven dated columns. */
final class WeeklyDriverScheduleParser {
    private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("h:mm a").toFormatter(Locale.US).withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern RANGE = Pattern.compile("(?i)^(\\d{1,2}:\\d{2}\\s*[AP]M)\\s*[-–—]\\s*(\\d{1,2}:\\d{2}\\s*[AP]M)$");
    static boolean header(ScheduleTableReader.Row row) {
        return row.values().contains("Employee") && row.values().containsAll(DAYS);
    }
    static ParsedSchedule parse(List<List<ScheduleTableReader.Row>> tables) {
        var coverage = new LinkedHashMap<String, Set<LocalDate>>();
        var shifts = new LinkedHashMap<Key, ShiftRecord>();
        for (var rows : tables) {
            Map<Integer, LocalDate> dates = null; int employeeColumn = -1, jobColumn = -1;
            String employee = null, job = "Unknown";
            for (int index = 0; index < rows.size(); index++) {
                var row = rows.get(index);
                if (header(row)) {
                    employeeColumn = row.values().indexOf("Employee"); jobColumn = row.values().indexOf("Primary Job");
                    if (++index >= rows.size()) throw invalid(row, "The weekly date row is missing.");
                    var dateRow = rows.get(index); dates = new LinkedHashMap<>(); LocalDate first = null;
                    for (int day = 0; day < 7; day++) {
                        int col = row.values().indexOf(DAYS.get(day)); LocalDate date;
                        try { date = LocalDate.parse(value(dateRow,col)); }
                        catch (DateTimeException e) { throw invalid(dateRow,"Each weekday needs a complete Excel date."); }
                        if (first == null) first = date;
                        if (date.getDayOfWeek().getValue() != day + 1 || !date.equals(first.plusDays(day)))
                            throw invalid(dateRow,"The weekly dates must run consecutively from Monday through Sunday.");
                        dates.put(col,date);
                    }
                    employee = null; continue;
                }
                if (dates == null) continue;
                String name = value(row,employeeColumn);
                if (!name.isBlank()) {
                    if (name.length() > 200) throw invalid(row,"Employee name is too long.");
                    employee = name; job = value(row,jobColumn);
                    coverage.computeIfAbsent(employee,k -> new TreeSet<>()).addAll(dates.values());
                }
                for (var day : dates.entrySet()) {
                    String text = value(row,day.getKey());
                    if (text.isBlank() || Set.of("OFF","DAY OFF","RDO").contains(text.toUpperCase(Locale.ROOT))) continue;
                    if (employee == null) throw invalid(row,"Work hours have no employee name.");
                    for (String line : text.split("\\R+")) {
                        var match = RANGE.matcher(line.strip());
                        if (!match.matches()) throw invalid(row,"Use work hours such as 9:00 AM - 2:00 PM, or leave days off blank.");
                        LocalTime start, end;
                        try { start = time(match.group(1)); end = time(match.group(2)); }
                        catch (DateTimeException e) { throw invalid(row,"The shift time is invalid."); }
                        if (start.equals(end)) throw invalid(row,"Shift start and end cannot be the same.");
                        var key = new Key(employee, day.getValue(), start, end);
                        shifts.putIfAbsent(key,new ShiftRecord("",employee,"Unknown",job.isBlank()?"Unknown":job,
                                day.getValue(),start,end,"Unknown location"));
                    }
                }
            }
        }
        if (coverage.isEmpty()) throw new IllegalStateException("The weekly report contains no named drivers.");
        return new ParsedSchedule(List.copyOf(shifts.values()),coverage);
    }
    private static LocalTime time(String text) { return LocalTime.parse(text.replaceAll("(?i)\\s*([AP]M)$"," $1"),TIME); }
    private static String value(ScheduleTableReader.Row row,int col) { return col < 0 || col >= row.size() ? "" : row.get(col).strip(); }
    private static IllegalStateException invalid(ScheduleTableReader.Row row,String reason) {
        return new IllegalStateException("Invalid weekly schedule in " + row.location() + ": " + reason);
    }
    private record Key(String name, LocalDate date, LocalTime start, LocalTime end) { }
}
