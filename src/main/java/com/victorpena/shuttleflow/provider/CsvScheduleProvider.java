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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.victorpena.shuttleflow.model.ShiftRecord;

@Component
public class CsvScheduleProvider implements ScheduleProvider {

    private static final String CSV_FILE = "data/schedules.csv";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("H:mm");

    private final Resource scheduleFile =
            new ClassPathResource(CSV_FILE);

    @Override
    public List<ShiftRecord> getSchedulesForDate(LocalDate date) {
        List<ShiftRecord> shifts = new ArrayList<>();

        try (
            Reader reader = new InputStreamReader(
                    scheduleFile.getInputStream(),
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

                if (shift.getShiftDate().equals(date)) {
                    shifts.add(shift);
                }
            }

            return shifts;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read " + CSV_FILE,
                    exception
            );
        }
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