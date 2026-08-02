package com.victorpena.shuttleflow.provider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

import com.victorpena.shuttleflow.model.ShiftRecord;

@Component
public class CsvScheduleProvider implements ScheduleProvider {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "M/d/yyyy h:mm a",
                    Locale.US
            );

    /*
     * Column positions in the Dayforce CSV.
     */
    private static final int EMPLOYEE_ID_COLUMN = 0;
    private static final int EMPLOYEE_NAME_COLUMN = 1;
    private static final int SHIFT_START_COLUMN = 2;
    private static final int SHIFT_END_COLUMN = 3;
    private static final int LOCATION_PATH_COLUMN = 4;
    private static final int PRIMARY_JOB_COLUMN = 5;

    /*
     * Stores the most recently uploaded schedule in memory.
     */
    private volatile List<ShiftRecord> uploadedShifts = List.of();

    public int loadSchedule(MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalStateException(
                    "Please select a CSV file to upload."
            );
        }

        List<ShiftRecord> parsedShifts = new ArrayList<>();
        boolean scheduleHeaderFound = false;

        try (
            Reader reader = new InputStreamReader(
                    file.getInputStream(),
                    StandardCharsets.UTF_8
            );

            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .get()
                    .parse(reader)
        ) {
            for (CSVRecord record : parser) {

                /*
                 * Dayforce puts seven report-information rows
                 * before the real CSV headings.
                 */
                if (!scheduleHeaderFound) {
                    if (isScheduleHeader(record)) {
                        scheduleHeaderFound = true;
                    }

                    continue;
                }

                /*
                 * Dayforce includes employees without a shift.
                 * Those rows have blank start and end times.
                 */
                if (doesNotContainScheduledShift(record)) {
                    continue;
                }

                parsedShifts.add(createShiftRecord(record));
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read the uploaded CSV file.",
                    exception
            );
        }

        if (!scheduleHeaderFound) {
            throw new IllegalStateException(
                    "The Dayforce schedule header could not be found."
            );
        }

        if (parsedShifts.isEmpty()) {
            throw new IllegalStateException(
                    "The uploaded CSV does not contain any scheduled shifts."
            );
        }

        uploadedShifts = List.copyOf(parsedShifts);

        return uploadedShifts.size();
    }

    @Override
    public List<ShiftRecord> getSchedulesForDate(LocalDate date) {

        return uploadedShifts.stream()
                .filter(shift -> shift.getShiftDate().equals(date))
                .toList();
    }

    public boolean hasUploadedSchedule() {
        return !uploadedShifts.isEmpty();
    }

    public List<LocalDate> getAvailableDates() {

        return uploadedShifts.stream()
                .map(ShiftRecord::getShiftDate)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean isScheduleHeader(CSVRecord record) {
        return record.size() > 0
                && "Employee ID".equalsIgnoreCase(
                        record.get(EMPLOYEE_ID_COLUMN).strip()
                );
    }

    private boolean doesNotContainScheduledShift(CSVRecord record) {
        return record.size() <= SHIFT_END_COLUMN
                || record.get(SHIFT_START_COLUMN).isBlank()
                || record.get(SHIFT_END_COLUMN).isBlank();
    }

    private ShiftRecord createShiftRecord(CSVRecord record) {

        try {
            LocalDateTime shiftStart = LocalDateTime.parse(
                    record.get(SHIFT_START_COLUMN).strip(),
                    DATE_TIME_FORMAT
            );

            LocalDateTime shiftEnd = LocalDateTime.parse(
                    record.get(SHIFT_END_COLUMN).strip(),
                    DATE_TIME_FORMAT
            );

            String locationPath =
                    record.get(LOCATION_PATH_COLUMN).strip();

            return new ShiftRecord(
                    record.get(EMPLOYEE_ID_COLUMN).strip(),
                    record.get(EMPLOYEE_NAME_COLUMN).strip(),
                    extractDepartment(locationPath),
                    record.get(PRIMARY_JOB_COLUMN).strip(),

                    /*
                     * Use the ending date because ShuttleFlow
                     * plans when the employee needs a pickup.
                     */
                    shiftEnd.toLocalDate(),

                    shiftStart.toLocalTime(),
                    shiftEnd.toLocalTime(),
                    extractPickupLocation(locationPath)
            );

        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Invalid data in Dayforce CSV record "
                            + record.getRecordNumber(),
                    exception
            );
        }
    }

    private String extractDepartment(String locationPath) {
        String[] pathParts = locationPath.split("/");

        /*
         * Example:
         * SF/SFFT/SFFT/RID/A1/1530 Pandemonium
         *
         * RID is the department code.
         */
        if (pathParts.length > 3) {
            return pathParts[3].strip();
        }

        return "Unknown";
    }

    private String extractPickupLocation(String locationPath) {
        int lastSlash = locationPath.lastIndexOf('/');

        String location = lastSlash >= 0
                ? locationPath.substring(lastSlash + 1)
                : locationPath;

        /*
         * Turns:
         * 1530 Pandemonium
         *
         * Into:
         * Pandemonium
         */
        location = location
                .replaceFirst("^\\d+\\s*", "")
                .strip();

        return location.isBlank()
                ? "Unknown location"
                : location;
    }
}