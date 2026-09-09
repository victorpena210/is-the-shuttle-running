package technology.renatus.routepulse.scheduling.importing;

import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Reads the current effective schedule from persisted uploaded shifts. */
@Primary
@Component
public class UploadedScheduleProvider implements ScheduleProvider {

    private final UploadedShiftRepository uploadedShiftRepository;

    public UploadedScheduleProvider(UploadedShiftRepository uploadedShiftRepository) {
        this.uploadedShiftRepository = uploadedShiftRepository;
    }

    @Override
    public List<ShiftRecord> getSchedulesForDate(Long organizationId, LocalDate date) {
        return uploadedShiftRepository.findLatestVersionForDate(organizationId, date)
                .stream()
                .map(this::toShiftRecord)
                .toList();
    }

    private ShiftRecord toShiftRecord(UploadedShift uploadedShift) {
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
}
