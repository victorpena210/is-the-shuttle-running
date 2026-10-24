package technology.renatus.routepulse.scheduling.importing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** Reads both the short and full Dayforce headings, including report preambles. */
@Component
public class DayforceCsvParser {
    private static final DateTimeFormatter DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("M/d/uuuu h:mm a").toFormatter(Locale.US)
            .withResolverStyle(ResolverStyle.STRICT);

    public List<ShiftRecord> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("Please select a Dayforce CSV file to upload.");
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalStateException("Upload a CSV no larger than 5 MB.");
        var shifts = new LinkedHashMap<ShiftKey, ShiftRecord>();
        Map<Field, Integer> columns = null;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(true).get().parse(reader)) {
            for (CSVRecord row : parser) {
                if (columns == null) {
                    if (row.stream().anyMatch(value -> normalize(value).equals("employeeid"))) columns = readHeader(row);
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
                    throw new IllegalStateException("Invalid data in Dayforce CSV record " + row.getRecordNumber()
                            + ": " + exception.getMessage(), exception);
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            throw new IllegalStateException("Could not read the file. Export the Dayforce schedule as a UTF-8 CSV and try again.", exception);
        }
        if (columns == null) throw new IllegalStateException("The Dayforce schedule header could not be found. Upload the exported CSV.");
        if (shifts.isEmpty()) throw new IllegalStateException("The uploaded CSV does not contain any scheduled shifts.");
        return List.copyOf(shifts.values());
    }

    private Map<Field, Integer> readHeader(CSVRecord row) {
        var columns = new EnumMap<Field, Integer>(Field.class);
        for (int i = 0; i < row.size(); i++) {
            String heading = normalize(row.get(i));
            for (Field field : Field.values()) if (field.aliases.contains(heading)) {
                if (columns.putIfAbsent(field, i) != null) throw new IllegalStateException("The CSV contains duplicate " + field.label + " columns.");
            }
        }
        for (Field field : List.of(Field.ID, Field.NAME, Field.START, Field.END))
            if (!columns.containsKey(field)) throw new IllegalStateException("The CSV is missing the " + field.label + " column.");
        return columns;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private String value(CSVRecord row, Map<Field, Integer> columns, Field field) {
        Integer index = columns.get(field);
        return index == null || index >= row.size() ? "" : row.get(index).strip();
    }
    private String required(CSVRecord row, Map<Field, Integer> columns, Field field, int length) {
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
