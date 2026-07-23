package com.victorpena.shuttleflow.provider;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.victorpena.shuttleflow.model.ShiftRecord;

@Component
public class CsvScheduleProvider implements ScheduleProvider {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");

    /*
     * This stores all shifts from the most recently uploaded CSV.
     *
     * The data stays in memory until:
     * 1. Another CSV is uploaded, or
     * 2. The application restarts.
     */
    private volatile List<ShiftRecord> uploadedShifts = List.of();

    public int loadSchedule(MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalStateException(
                    "Please select a CSV file to upload."
            );
        }

        List<ShiftRecord> parsedShifts = new ArrayList<>();

        try (
            Reader reader = new InputStreamReader(
                    file.getInputStream(),
                    StandardCharsets.UTF_8
            );

            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .get()
                    .parse(reader)
        ) {
            for (CSVRecord record : parser) {
                ShiftRecord shift = createShiftRecord(record);
                parsedShifts.add(shift);
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read the uploaded CSV file.",
                    exception
            );
        }

        if (parsedShifts.isEmpty()) {
            throw new IllegalStateException(
                    "The uploaded CSV does not contain any shifts."
            );
        }

        /*
         * Replace the old schedule with the newly uploaded schedule.
         */
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

    private ShiftRecord createShiftRecord(CSVRecord record) {

        try {
            return new ShiftRecord(
                    record.get("employee_id"),
                    record.get("employee_name"),
                    record.get("department"),
                    record.get("job"),
                    LocalDate.parse(record.get("shift_date")),
                    LocalTime.parse(
                            record.get("start_time"),
                            TIME_FORMAT
                    ),
                    LocalTime.parse(
                            record.get("end_time"),
                            TIME_FORMAT
                    ),
                    record.get("pickup_location")
            );

        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Invalid data in CSV record "
                            + record.getRecordNumber(),
                    exception
            );
        }
    }
}