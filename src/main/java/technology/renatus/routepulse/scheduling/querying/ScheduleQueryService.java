package technology.renatus.routepulse.scheduling.querying;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.repository.ScheduleImportRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Provides schedule/import metadata used by dashboards and planning controls. */
@Service
public class ScheduleQueryService {

    private final UploadedShiftRepository uploadedShiftRepository;
    private final ScheduleImportRepository scheduleImportRepository;

    public ScheduleQueryService(
            UploadedShiftRepository uploadedShiftRepository,
            ScheduleImportRepository scheduleImportRepository) {
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.scheduleImportRepository = scheduleImportRepository;
    }

    public boolean hasUploadedSchedule(Long organizationId) {
        return uploadedShiftRepository.existsByOrganization_Id(organizationId);
    }

    public List<LocalDate> getAvailableDates(Long organizationId) {
        return uploadedShiftRepository.findAvailableDates(organizationId);
    }

    public LocalDate getFirstDateOfLatestImport(Long organizationId) {
        return scheduleImportRepository.findFirstByOrganization_IdOrderByUploadedAtDesc(organizationId)
                .map(ScheduleImport::getFirstShiftDate)
                // Fallback for rows created before import history was introduced.
                .or(() -> uploadedShiftRepository.findFirstDateOfLatestImport(organizationId))
                .orElseThrow(() -> new IllegalStateException("No uploaded schedule could be found."));
    }

    public List<ScheduleImport> getRecentImports(Long organizationId) {
        return scheduleImportRepository.findTop10ByOrganization_IdOrderByUploadedAtDesc(organizationId);
    }

    public Optional<ScheduleImport> getLatestImportForDate(Long organizationId, LocalDate date) {
        return uploadedShiftRepository.findLatestScheduleImportForDate(organizationId, date);
    }
}
