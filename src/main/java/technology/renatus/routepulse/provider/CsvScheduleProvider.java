package technology.renatus.routepulse.provider;

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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShiftRecord;
import technology.renatus.routepulse.model.UploadedShift;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.repository.OrganizationRepository;
import technology.renatus.routepulse.repository.ScheduleImportRepository;
import technology.renatus.routepulse.repository.UploadedShiftRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;

@Component
public class CsvScheduleProvider
        implements ScheduleProvider {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "M/d/yyyy h:mm a",
                    Locale.US
            );

    private static final int EMPLOYEE_ID_COLUMN = 0;
    private static final int EMPLOYEE_NAME_COLUMN = 1;
    private static final int SHIFT_START_COLUMN = 2;
    private static final int SHIFT_END_COLUMN = 3;
    private static final int LOCATION_PATH_COLUMN = 4;
    private static final int PRIMARY_JOB_COLUMN = 5;

    private final UploadedShiftRepository
            uploadedShiftRepository;

    private final OrganizationRepository
            organizationRepository;

    private final ScheduleImportRepository
            scheduleImportRepository;

    private final UserAccountRepository
            userAccountRepository;

    public CsvScheduleProvider(
            UploadedShiftRepository uploadedShiftRepository,
            OrganizationRepository organizationRepository,
            ScheduleImportRepository scheduleImportRepository,
            UserAccountRepository userAccountRepository) {

        this.uploadedShiftRepository =
                uploadedShiftRepository;

        this.organizationRepository =
                organizationRepository;

        this.scheduleImportRepository =
                scheduleImportRepository;

        this.userAccountRepository =
                userAccountRepository;
    }

    @Transactional
    public ScheduleImport loadSchedule(
            Long organizationId,
            String uploaderEmail,
            MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalStateException(
                    "Please select a CSV file to upload."
            );
        }

        List<ShiftRecord> parsedShifts =
                new ArrayList<>();

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
                 * Dayforce puts report-information rows before
                 * the actual CSV heading.
                 */
                if (!scheduleHeaderFound) {
                    if (isScheduleHeader(record)) {
                        scheduleHeaderFound = true;
                    }

                    continue;
                }

                /*
                 * Dayforce includes employees without shifts.
                 * Those rows have blank start and end times.
                 */
                if (doesNotContainScheduledShift(record)) {
                    continue;
                }

                parsedShifts.add(
                        createShiftRecord(record)
                );
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
                    "The uploaded CSV does not contain "
                            + "any scheduled shifts."
            );
        }

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The organization could not be found."
                                )
                        );

        UserAccount uploadedBy =
                userAccountRepository
                        .findByEmailIgnoreCase(
                                uploaderEmail
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The signed-in user could not be found."
                                )
                        );

        /*
         * Find every unique date contained in this CSV.
         */
        Set<LocalDate> uploadedDateSet =
                parsedShifts.stream()
                        .map(ShiftRecord::getShiftDate)
                        .collect(Collectors.toSet());

        List<LocalDate> uploadedDates =
                uploadedDateSet.stream()
                        .sorted()
                        .toList();

        LocalDate firstShiftDate =
                uploadedDates.get(0);

        LocalDate lastShiftDate =
                uploadedDates.get(
                        uploadedDates.size() - 1
                );

        String datesRepresented =
                uploadedDates.stream()
                        .map(LocalDate::toString)
                        .collect(
                                Collectors.joining(",")
                        );

        LocalDateTime uploadedAt =
                LocalDateTime.now();

        /*
         * Create exactly one audit/history record for this upload.
         * The UUID is the import/version ID that can be shown to
         * managers or used later for analytics and troubleshooting.
         */
        ScheduleImport scheduleImport =
                new ScheduleImport(
                        organization,
                        uploadedBy,
                        UUID.randomUUID().toString(),
                        cleanOriginalFilename(file),
                        parsedShifts.size(),
                        firstShiftDate,
                        lastShiftDate,
                        datesRepresented,
                        uploadedAt
                );

        scheduleImportRepository.save(
                scheduleImport
        );

        /*
         * Do NOT delete older rows here.
         *
         * Every upload is kept as a permanent schedule version
         * for pilot history, analytics, troubleshooting, and
         * auditing.
         *
         * Normal schedule reads use only the rows with the newest
         * importedAt value for each organization/date.
         *
         * This means:
         *
         * Import #1 remains stored.
         * Import #2 remains stored.
         * Import #3 remains stored.
         *
         * But RoutePulse uses Import #3 when it is the newest
         * version containing the requested date.
         */
        List<UploadedShift> uploadedShifts =
                parsedShifts.stream()
                        .map(shift ->
                                toUploadedShift(
                                        organization,
                                        scheduleImport,
                                        shift,
                                        uploadedAt
                                )
                        )
                        .toList();

        uploadedShiftRepository.saveAll(
                uploadedShifts
        );

        return scheduleImport;
    }

    @Override
    public List<ShiftRecord> getSchedulesForDate(
            Long organizationId,
            LocalDate date) {

        /*
         * Only return the newest version of this date.
         *
         * Historical versions stay in MySQL but do not appear
         * in the current planning calculations.
         */
        return uploadedShiftRepository
                .findLatestVersionForDate(
                        organizationId,
                        date
                )
                .stream()
                .map(this::toShiftRecord)
                .toList();
    }

    public boolean hasUploadedSchedule(
            Long organizationId) {

        return uploadedShiftRepository
                .existsByOrganization_Id(
                        organizationId
                );
    }

    public List<LocalDate> getAvailableDates(
            Long organizationId) {

        return uploadedShiftRepository
                .findAvailableDates(
                        organizationId
                );
    }

    public LocalDate getFirstDateOfLatestImport(
            Long organizationId) {

        return scheduleImportRepository
                .findFirstByOrganization_IdOrderByUploadedAtDesc(
                        organizationId
                )
                .map(
                        ScheduleImport::getFirstShiftDate
                )
                /*
                 * Fallback for schedule rows that already existed
                 * before migration V14 introduced import history.
                 */
                .or(() ->
                        uploadedShiftRepository
                                .findFirstDateOfLatestImport(
                                        organizationId
                                )
                )
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No uploaded schedule could be found."
                        )
                );
    }

    public List<ScheduleImport> getRecentImports(
            Long organizationId) {

        return scheduleImportRepository
                .findTop10ByOrganization_IdOrderByUploadedAtDesc(
                        organizationId
                );
    }

    private UploadedShift toUploadedShift(
            Organization organization,
            ScheduleImport scheduleImport,
            ShiftRecord shift,
            LocalDateTime importedAt) {

        return new UploadedShift(
                organization,
                scheduleImport,
                shift.getEmployeeId(),
                shift.getEmployeeName(),
                shift.getDepartment(),
                shift.getJob(),
                shift.getShiftDate(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getPickupLocation(),
                importedAt
        );
    }

    private ShiftRecord toShiftRecord(
            UploadedShift uploadedShift) {

        return new ShiftRecord(
                uploadedShift.getEmployeeId(),
                uploadedShift.getEmployeeName(),
                uploadedShift.getDepartment(),
                uploadedShift.getJob(),
                uploadedShift.getShiftDate(),
                uploadedShift.getStartTime(),
                uploadedShift.getEndTime(),
                uploadedShift.getPickupLocation()
        );
    }

    private String cleanOriginalFilename(
            MultipartFile file) {

        String originalFilename =
                file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {

            return "schedule.csv";
        }

        String cleaned = originalFilename
                .replace('\\', '/');

        int lastSlash =
                cleaned.lastIndexOf('/');

        if (lastSlash >= 0) {
            cleaned = cleaned.substring(
                    lastSlash + 1
            );
        }

        cleaned = cleaned.strip();

        if (cleaned.isBlank()) {
            return "schedule.csv";
        }

        if (cleaned.length() > 255) {
            return cleaned.substring(0, 255);
        }

        return cleaned;
    }

    private boolean isScheduleHeader(
            CSVRecord record) {

        return record.size() > 0
                && "Employee ID".equalsIgnoreCase(
                        record.get(
                                EMPLOYEE_ID_COLUMN
                        ).strip()
                );
    }

    private boolean doesNotContainScheduledShift(
            CSVRecord record) {

        return record.size() <= SHIFT_END_COLUMN
                || record.get(
                        SHIFT_START_COLUMN
                ).isBlank()
                || record.get(
                        SHIFT_END_COLUMN
                ).isBlank();
    }

    private ShiftRecord createShiftRecord(
            CSVRecord record) {

        try {
            LocalDateTime shiftStart =
                    LocalDateTime.parse(
                            record.get(
                                    SHIFT_START_COLUMN
                            ).strip(),
                            DATE_TIME_FORMAT
                    );

            LocalDateTime shiftEnd =
                    LocalDateTime.parse(
                            record.get(
                                    SHIFT_END_COLUMN
                            ).strip(),
                            DATE_TIME_FORMAT
                    );

            String locationPath =
                    record.get(
                            LOCATION_PATH_COLUMN
                    ).strip();

            return new ShiftRecord(
                    record.get(
                            EMPLOYEE_ID_COLUMN
                    ).strip(),
                    record.get(
                            EMPLOYEE_NAME_COLUMN
                    ).strip(),
                    extractDepartment(
                            locationPath
                    ),
                    record.get(
                            PRIMARY_JOB_COLUMN
                    ).strip(),
                    shiftEnd.toLocalDate(),
                    shiftStart.toLocalTime(),
                    shiftEnd.toLocalTime(),
                    extractPickupLocation(
                            locationPath
                    )
            );

        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Invalid data in Dayforce CSV record "
                            + record.getRecordNumber(),
                    exception
            );
        }
    }

    private String extractDepartment(
            String locationPath) {

        String[] pathParts =
                locationPath.split("/");

        if (pathParts.length > 3) {
            return pathParts[3].strip();
        }

        return "Unknown";
    }

    private String extractPickupLocation(
            String locationPath) {

        int lastSlash =
                locationPath.lastIndexOf('/');

        String location = lastSlash >= 0
                ? locationPath.substring(
                        lastSlash + 1
                )
                : locationPath;

        location = location
                .replaceFirst(
                        "^\\d+\\s*",
                        ""
                )
                .strip();

        return location.isBlank()
                ? "Unknown location"
                : location;
    }
}