package technology.renatus.routepulse.scheduling.importing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.repository.ScheduleImportRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Coordinates a schedule upload after the file has been parsed. */
@Service
public class ScheduleImportService {

    private final DayforceCsvParser dayforceCsvParser;
    private final UploadedShiftRepository uploadedShiftRepository;
    private final OrganizationRepository organizationRepository;
    private final ScheduleImportRepository scheduleImportRepository;
    private final UserAccountRepository userAccountRepository;

    public ScheduleImportService(
            DayforceCsvParser dayforceCsvParser,
            UploadedShiftRepository uploadedShiftRepository,
            OrganizationRepository organizationRepository,
            ScheduleImportRepository scheduleImportRepository,
            UserAccountRepository userAccountRepository) {
        this.dayforceCsvParser = dayforceCsvParser;
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.organizationRepository = organizationRepository;
        this.scheduleImportRepository = scheduleImportRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public ScheduleImport importSchedule(Long organizationId, String uploaderEmail, MultipartFile file) {
        List<ShiftRecord> parsedShifts = dayforceCsvParser.parse(file);
        Organization organization = loadOrganization(organizationId);
        UserAccount uploadedBy = loadUploader(uploaderEmail);

        List<LocalDate> uploadedDates = parsedShifts.stream()
                .map(ShiftRecord::getShiftDate)
                .distinct()
                .sorted()
                .toList();

        LocalDateTime uploadedAt = LocalDateTime.now();
        ScheduleImport scheduleImport = new ScheduleImport(
                organization,
                uploadedBy,
                UUID.randomUUID().toString(),
                cleanOriginalFilename(file),
                parsedShifts.size(),
                uploadedDates.get(0),
                uploadedDates.get(uploadedDates.size() - 1),
                uploadedDates.stream().map(LocalDate::toString).collect(Collectors.joining(",")),
                uploadedAt
        );

        scheduleImportRepository.save(scheduleImport);

        // Versions remain available through the operating day for reconciliation.
        // Nightly retention later archives anonymous demand and purges the raw rows.
        List<UploadedShift> uploadedShifts = parsedShifts.stream()
                .map(shift -> toUploadedShift(organization, scheduleImport, shift, uploadedAt))
                .toList();
        uploadedShiftRepository.saveAll(uploadedShifts);

        return scheduleImport;
    }

    private Organization loadOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("The organization could not be found."));
    }

    private UserAccount loadUploader(String uploaderEmail) {
        return userAccountRepository.findByEmailIgnoreCase(uploaderEmail)
                .orElseThrow(() -> new IllegalStateException("The signed-in user could not be found."));
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

    private String cleanOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "schedule.csv";
        }

        String cleaned = originalFilename.replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }

        cleaned = cleaned.strip();
        if (cleaned.isBlank()) {
            return "schedule.csv";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
