package technology.renatus.routepulse.scheduling.retention.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.planning.TransportationDemandService;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
import technology.renatus.routepulse.scheduling.retention.model.HistoricalDemandSnapshot;
import technology.renatus.routepulse.scheduling.retention.model.HistoricalDemandWindow;
import technology.renatus.routepulse.scheduling.retention.repository.HistoricalDemandSnapshotRepository;

/**
 * Converts completed employee schedules into anonymous forecasting history,
 * then permanently removes the employee-level schedule rows.
 */
@Service
public class HistoricalScheduleRetentionService {

    private final UploadedShiftRepository uploadedShiftRepository;
    private final HistoricalDemandSnapshotRepository snapshotRepository;
    private final TransportationDemandService transportationDemandService;
    private final ZoneId operationsZone;
    private final int forecastHistoryMonths;
    private final int windowMinutes;

    public HistoricalScheduleRetentionService(
            UploadedShiftRepository uploadedShiftRepository,
            HistoricalDemandSnapshotRepository snapshotRepository,
            TransportationDemandService transportationDemandService,
            @Value("${app.operations-zone:America/Chicago}") String operationsZone,
            @Value("${app.retention.forecast-history-months:12}") int forecastHistoryMonths,
            @Value("${app.transportation-demand.window-minutes:15}") int windowMinutes) {

        if (forecastHistoryMonths <= 0) {
            throw new IllegalArgumentException(
                    "Forecast-history retention months must be greater than zero."
            );
        }
        if (windowMinutes <= 0 || 60 % windowMinutes != 0) {
            throw new IllegalArgumentException(
                    "Historical demand window minutes must be a positive divisor of 60."
            );
        }

        this.uploadedShiftRepository = uploadedShiftRepository;
        this.snapshotRepository = snapshotRepository;
        this.transportationDemandService = transportationDemandService;
        this.operationsZone = ZoneId.of(operationsZone);
        this.forecastHistoryMonths = forecastHistoryMonths;
        this.windowMinutes = windowMinutes;
    }

    @Transactional
    public ScheduleRetentionResult archiveAndPurgeCompletedDays() {
        return archiveAndPurgeBefore(LocalDate.now(operationsZone));
    }

    /**
     * Archives every raw schedule date strictly before currentOperatingDate.
     * The explicit date entry point also makes the retention boundary testable.
     */
    @Transactional
    public ScheduleRetentionResult archiveAndPurgeBefore(
            LocalDate currentOperatingDate) {

        if (currentOperatingDate == null) {
            throw new IllegalArgumentException("Current operating date is required.");
        }

        Instant snapshotTime = Instant.now();
        int archivedDays = 0;

        List<Long> organizationIds = uploadedShiftRepository
                .findOrganizationIdsWithShiftDateBefore(currentOperatingDate);

        for (Long organizationId : organizationIds) {
            List<LocalDate> completedDates = uploadedShiftRepository
                    .findShiftDatesBefore(organizationId, currentOperatingDate);

            for (LocalDate operatingDate : completedDates) {
                List<UploadedShift> effectiveShifts = uploadedShiftRepository
                        .findLatestVersionForDate(organizationId, operatingDate);

                if (effectiveShifts.isEmpty()) {
                    continue;
                }

                archiveDay(
                        organizationId,
                        operatingDate,
                        effectiveShifts,
                        snapshotTime
                );
                archivedDays++;
            }
        }

        // Force the anonymous snapshots to the database before the bulk raw-data delete.
        snapshotRepository.flush();

        int purgedRows = uploadedShiftRepository
                .deleteShiftRowsBefore(currentOperatingDate);

        LocalDate historicalCutoff = currentOperatingDate
                .minusMonths(forecastHistoryMonths);

        long expiredSnapshots = snapshotRepository
                .deleteByOperatingDateBefore(historicalCutoff);

        return new ScheduleRetentionResult(
                archivedDays,
                purgedRows,
                expiredSnapshots
        );
    }

    private void archiveDay(
            Long organizationId,
            LocalDate operatingDate,
            List<UploadedShift> effectiveShifts,
            Instant snapshotTime) {

        Organization organization = effectiveShifts.get(0).getOrganization();
        ScheduleImport sourceScheduleImport = effectiveShifts.get(0).getScheduleImport();

        TransportationDemandAnalysis demand = transportationDemandService
                .getDemandForDate(organizationId, operatingDate);

        int employeeCount = new HashSet<>(
                effectiveShifts.stream()
                        .map(UploadedShift::getEmployeeId)
                        .toList()
        ).size();

        HistoricalDemandSnapshot snapshot = snapshotRepository
                .findByOrganization_IdAndOperatingDate(
                        organizationId,
                        operatingDate
                )
                .orElseGet(() -> new HistoricalDemandSnapshot(
                        organization,
                        operatingDate
                ));

        snapshot.refresh(
                sourceScheduleImport,
                effectiveShifts.size(),
                employeeCount,
                demand.getMorning().getTotalCount(),
                demand.getMidday().getTotalCount(),
                demand.getEvening().getTotalCount(),
                demand.getOffHoursMovementCount(),
                demand.getMorning().getRecommendedStartTime(),
                demand.getMorning().getRecommendedEndTime(),
                demand.getOverallPeakWindowStart(),
                demand.getOverallPeakWindowEnd(),
                demand.getOverallPeakWindowCount(),
                demand.getOverallPeakPeriodTitle(),
                snapshotTime
        );

        snapshot.replaceWindows(buildAnonymousWindows(effectiveShifts));
        snapshotRepository.save(snapshot);
    }

    private List<HistoricalDemandWindow> buildAnonymousWindows(
            List<UploadedShift> shifts) {

        Map<LocalTime, MutableWindowCounts> counts = new TreeMap<>();

        for (UploadedShift shift : shifts) {
            addArrival(counts, shift.getStartTime());
            addDeparture(counts, shift.getEndTime());
        }

        List<HistoricalDemandWindow> windows = new ArrayList<>();
        counts.forEach((windowStart, count) -> windows.add(
                new HistoricalDemandWindow(
                        windowStart,
                        windowStart.plusMinutes(windowMinutes),
                        count.arrivals,
                        count.departures
                )
        ));

        return List.copyOf(windows);
    }

    private void addArrival(
            Map<LocalTime, MutableWindowCounts> counts,
            LocalTime time) {
        MutableWindowCounts window = counts.computeIfAbsent(
                floorToWindow(time),
                ignored -> new MutableWindowCounts()
        );
        window.arrivals++;
    }

    private void addDeparture(
            Map<LocalTime, MutableWindowCounts> counts,
            LocalTime time) {
        MutableWindowCounts window = counts.computeIfAbsent(
                floorToWindow(time),
                ignored -> new MutableWindowCounts()
        );
        window.departures++;
    }

    private LocalTime floorToWindow(LocalTime time) {
        int roundedMinute = (time.getMinute() / windowMinutes) * windowMinutes;
        return time.withMinute(roundedMinute).withSecond(0).withNano(0);
    }

    private static final class MutableWindowCounts {
        private int arrivals;
        private int departures;
    }
}
