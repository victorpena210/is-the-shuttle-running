package technology.renatus.routepulse.scheduling.importing;

import java.time.*;
import java.time.format.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** Reads both the short and full Dayforce headings, from CSV or XLSX, including report preambles. */
@Component
public class DayforceCsvParser {
    private static final DateTimeFormatter DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("M/d/uuuu h:mm a").toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT);

    public List<ShiftRecord> parse(MultipartFile file) {
        return parseSchedule(file).shifts();
    }
    public ParsedSchedule parseSchedule(MultipartFile file) {
        var tables = ScheduleTableReader.read(file);
        boolean weekly = tables.stream().flatMap(Collection::stream).anyMatch(WeeklyDriverScheduleParser::header);
        if (weekly) {
            if (tables.stream().flatMap(Collection::stream).anyMatch(r -> r.values().stream().anyMatch(v -> normalize(v).equals("employeeid"))))
                throw new IllegalStateException("Upload weekly driver grids and employee demand tables separately.");
            return WeeklyDriverScheduleParser.parse(tables);
        }
        var shifts = new LinkedHashMap<ShiftKey, ShiftRecord>();
        boolean foundHeader = false;
        for (var table : tables) {
            Map<Field, Integer> columns = null;
            for (var row : table) {
                if (columns == null) {
                    if (row.values().stream().anyMatch(value -> normalize(value).equals("employeeid"))) {
                        columns = readHeader(row); foundHeader = true;
                    }
                    continue;
                }
                String startText = value(row, columns, Field.START);
                String endText = value(row, columns, Field.END);
                if (startText.isBlank() && endText.isBlank()) continue;
                try {
                    String id = required(row, columns, Field.ID, 50);
                    String name = required(row, columns, Field.NAME, 200);
                    LocalDateTime start = LocalDateTime.parse(required(row, columns, Field.START, 50), DATE_TIME_FORMAT);
                    LocalDateTime end = LocalDateTime.parse(required(row, columns, Field.END, 50), DATE_TIME_FORMAT);
                    if (!end.isAfter(start)) throw new IllegalArgumentException("Shift End must be after Shift Start.");
                    if (Duration.between(start, end).compareTo(Duration.ofHours(24)) > 0)
                        throw new IllegalArgumentException("A shift cannot exceed 24 hours. Check its dates.");
                    String location = limited(value(row, columns, Field.LOCATION), 1000, "Location Path");
                    String[] parts = location.split("/");
                    String department = limited(parts.length > 3 ? parts[3].strip() : "Unknown", 200, "Department");
                    String pickup = location.substring(location.lastIndexOf('/') + 1).replaceFirst("^\\d+\\s*", "").strip();
                    pickup = limited(pickup.isBlank() ? "Unknown location" : pickup, 200, "Pickup Location");
                    String job = limited(value(row, columns, Field.JOB), 200, "Primary Job");
                    shifts.putIfAbsent(new ShiftKey(id, start, end), new ShiftRecord(id, name, department,
                            job.isBlank() ? "Unknown" : job, start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), pickup));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Invalid data in schedule " + row.location()
                            + ": " + exception.getMessage(), exception);
                }
            }
        }
        if (!foundHeader) throw new IllegalStateException("The Dayforce schedule header could not be found. Upload a CSV or XLSX with the required columns.");
        if (shifts.isEmpty()) throw new IllegalStateException("The uploaded schedule does not contain any scheduled shifts.");
        return new ParsedSchedule(List.copyOf(shifts.values()),Map.of());
    }

    private Map<Field, Integer> readHeader(ScheduleTableReader.Row row) {
        var columns = new EnumMap<Field, Integer>(Field.class);
        for (int i = 0; i < row.size(); i++) {
            String heading = normalize(row.get(i));
            for (Field field : Field.values()) if (field.aliases.contains(heading)) {
                if (columns.putIfAbsent(field, i) != null) throw new IllegalStateException("The schedule contains duplicate " + field.label + " columns.");
            }
        }
        for (Field field : List.of(Field.ID, Field.NAME, Field.START, Field.END))
            if (!columns.containsKey(field)) throw new IllegalStateException("The schedule is missing the " + field.label + " column.");
        return columns;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private String value(ScheduleTableReader.Row row, Map<Field, Integer> columns, Field field) {
        Integer index = columns.get(field);
        return index == null || index >= row.size() ? "" : row.get(index).strip();
    }
    private String required(ScheduleTableReader.Row row, Map<Field, Integer> columns, Field field, int length) {
        String text = value(row, columns, field);
        if (text.isBlank()) throw new IllegalArgumentException(field.label + " is required.");
        return limited(text, length, field.label);
    }
    private String limited(String value, int length, String label) {
        if (value.length() > length) throw new IllegalArgumentException(label + " cannot exceed " + length + " characters.");
        return value;
    }
    private record ShiftKey(String employeeId, LocalDateTime start, LocalDateTime end) {}
    private enum Field {
        ID("Employee ID", "employeeid"), NAME("Employee Name", "employeename", "employeefullname"),
        START("Shift Start", "shiftstart", "scheduledshiftstartdatetime"),
        END("Shift End", "shiftend", "scheduledshiftenddatetime"),
        LOCATION("Location Path", "locationpath", "primarylocationpath"), JOB("Primary Job", "primaryjob");
        final String label; final Set<String> aliases;
        Field(String label, String... aliases) { this.label = label; this.aliases = Set.of(aliases); }
    }
}
