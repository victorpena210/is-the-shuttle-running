package technology.renatus.routepulse.scheduling.importing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.querying.ScheduleQueryService;
import technology.renatus.routepulse.scheduling.repository.ScheduleImportRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/**
 * Backward-compatible facade for schedule upload/read operations.
 *
 * New code is split between DayforceCsvParser, ScheduleImportService,
 * UploadedScheduleProvider, and ScheduleQueryService.
 */
@Component
public class CsvScheduleProvider implements ScheduleProvider {

    private final ScheduleImportService scheduleImportService;
    private final UploadedScheduleProvider uploadedScheduleProvider;
    private final ScheduleQueryService scheduleQueryService;

    @Autowired
    public CsvScheduleProvider(
            ScheduleImportService scheduleImportService,
            UploadedScheduleProvider uploadedScheduleProvider,
            ScheduleQueryService scheduleQueryService) {
        this.scheduleImportService = scheduleImportService;
        this.uploadedScheduleProvider = uploadedScheduleProvider;
        this.scheduleQueryService = scheduleQueryService;
    }

    /** Keeps older tests/callers source-compatible while the facade is phased out. */
    public CsvScheduleProvider(
            UploadedShiftRepository uploadedShiftRepository,
            OrganizationRepository organizationRepository,
            ScheduleImportRepository scheduleImportRepository,
            UserAccountRepository userAccountRepository) {
        DayforceCsvParser parser = new DayforceCsvParser();
        this.scheduleImportService = new ScheduleImportService(
                parser,
                uploadedShiftRepository,
                organizationRepository,
                scheduleImportRepository,
                userAccountRepository
        );
        this.uploadedScheduleProvider = new UploadedScheduleProvider(uploadedShiftRepository);
        this.scheduleQueryService = new ScheduleQueryService(uploadedShiftRepository, scheduleImportRepository);
    }

    public ScheduleImport loadSchedule(Long organizationId, String uploaderEmail, MultipartFile file) {
        return scheduleImportService.importSchedule(organizationId, uploaderEmail, file);
    }

    @Override
    public List<ShiftRecord> getSchedulesForDate(Long organizationId, LocalDate date) {
        return uploadedScheduleProvider.getSchedulesForDate(organizationId, date);
    }

    public boolean hasUploadedSchedule(Long organizationId) {
        return scheduleQueryService.hasUploadedSchedule(organizationId);
    }

    public List<LocalDate> getAvailableDates(Long organizationId) {
        return scheduleQueryService.getAvailableDates(organizationId);
    }

    public LocalDate getFirstDateOfLatestImport(Long organizationId) {
        return scheduleQueryService.getFirstDateOfLatestImport(organizationId);
    }

    public List<ScheduleImport> getRecentImports(Long organizationId) {
        return scheduleQueryService.getRecentImports(organizationId);
    }

    public Optional<ScheduleImport> getLatestImportForDate(Long organizationId, LocalDate date) {
        return scheduleQueryService.getLatestImportForDate(organizationId, date);
    }
}
