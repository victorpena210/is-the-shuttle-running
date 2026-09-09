package technology.renatus.routepulse.scheduling.importing;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** Knows how to translate a Dayforce schedule export into RoutePulse shift records. */
@Component
public class DayforceCsvParser {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US);

    private static final int EMPLOYEE_ID_COLUMN = 0;
    private static final int EMPLOYEE_NAME_COLUMN = 1;
    private static final int SHIFT_START_COLUMN = 2;
    private static final int SHIFT_END_COLUMN = 3;
    private static final int LOCATION_PATH_COLUMN = 4;
    private static final int PRIMARY_JOB_COLUMN = 5;

    public List<ShiftRecord> parse(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalStateException("Please select a CSV file to upload.");
        }

        List<ShiftRecord> parsedShifts = new ArrayList<>();
        boolean scheduleHeaderFound = false;

        try (
                Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .get()
                        .parse(reader)
        ) {
            for (CSVRecord record : parser) {
                // Dayforce puts report-information rows before the actual CSV heading.
                if (!scheduleHeaderFound) {
                    if (isScheduleHeader(record)) {
                        scheduleHeaderFound = true;
                    }
                    continue;
                }

                // Dayforce includes employees without shifts as rows with blank start/end times.
                if (doesNotContainScheduledShift(record)) {
                    continue;
                }

                parsedShifts.add(createShiftRecord(record));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the uploaded CSV file.", exception);
        }

        if (!scheduleHeaderFound) {
            throw new IllegalStateException("The Dayforce schedule header could not be found.");
        }

        if (parsedShifts.isEmpty()) {
            throw new IllegalStateException("The uploaded CSV does not contain any scheduled shifts.");
        }

        return List.copyOf(parsedShifts);
    }

    private boolean isScheduleHeader(CSVRecord record) {
        return record.size() > 0
                && "Employee ID".equalsIgnoreCase(record.get(EMPLOYEE_ID_COLUMN).strip());
    }

    private boolean doesNotContainScheduledShift(CSVRecord record) {
        // A row with only one blank time is malformed and must be validated below.
        return record.size() > SHIFT_END_COLUMN
                && record.get(SHIFT_START_COLUMN).isBlank()
                && record.get(SHIFT_END_COLUMN).isBlank();
    }

    private ShiftRecord createShiftRecord(CSVRecord record) {
        try {
            String employeeId = requireValue(record, EMPLOYEE_ID_COLUMN, "Employee ID", 50);
            String employeeName = requireValue(record, EMPLOYEE_NAME_COLUMN, "Employee Name", 200);

            LocalDateTime shiftStart = LocalDateTime.parse(
                    requireValue(record, SHIFT_START_COLUMN, "Shift Start", 50),
                    DATE_TIME_FORMAT
            );

            LocalDateTime shiftEnd = LocalDateTime.parse(
                    requireValue(record, SHIFT_END_COLUMN, "Shift End", 50),
                    DATE_TIME_FORMAT
            );

            if (!shiftEnd.isAfter(shiftStart)) {
                throw new IllegalArgumentException("Shift End must be after Shift Start.");
            }

            String locationPath = optionalValue(record, LOCATION_PATH_COLUMN, 1000, "Location Path");
            String department = limitValue(extractDepartment(locationPath), 200, "Department");
            String job = optionalValue(record, PRIMARY_JOB_COLUMN, 200, "Primary Job");
            String pickupLocation = limitValue(extractPickupLocation(locationPath), 200, "Pickup Location");

            if (job.isBlank()) {
                job = "Unknown";
            }

            return new ShiftRecord(
                    employeeId,
                    employeeName,
                    department,
                    job,
                    // The workday belongs to the date the employee clocks in, including midnight-ending shifts.
                    shiftStart.toLocalDate(),
                    shiftStart.toLocalTime(),
                    shiftEnd.toLocalTime(),
                    pickupLocation
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Invalid data in Dayforce CSV record " + record.getRecordNumber(),
                    exception
            );
        }
    }

    private String requireValue(CSVRecord record, int column, String fieldName, int maximumLength) {
        String value = optionalValue(record, column, maximumLength, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }

    private String optionalValue(CSVRecord record, int column, int maximumLength, String fieldName) {
        if (record.size() <= column) {
            throw new IllegalArgumentException(fieldName + " column is missing.");
        }
        return limitValue(record.get(column).strip(), maximumLength, fieldName);
    }

    private String limitValue(String value, int maximumLength, String fieldName) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " cannot exceed " + maximumLength + " characters."
            );
        }
        return value;
    }

    private String extractDepartment(String locationPath) {
        String[] pathParts = locationPath.split("/");
        return pathParts.length > 3 ? pathParts[3].strip() : "Unknown";
    }

    private String extractPickupLocation(String locationPath) {
        int lastSlash = locationPath.lastIndexOf('/');
        String location = lastSlash >= 0 ? locationPath.substring(lastSlash + 1) : locationPath;
        location = location.replaceFirst("^\\d+\\s*", "").strip();
        return location.isBlank() ? "Unknown location" : location;
    }
}
