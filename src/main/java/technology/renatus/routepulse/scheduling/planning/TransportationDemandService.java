package technology.renatus.routepulse.scheduling.planning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;
import technology.renatus.routepulse.scheduling.planning.common.DemandPriorityClassifier;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationDemandBucketizer;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationDemandPeriodBuilder;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationPeriodType;

/**
 * Builds the manager-facing transportation plan from uploaded schedules.
 * Driver selection and fairness remain outside this service.
 */
@Service
public class TransportationDemandService {

    private final ScheduleProvider scheduleProvider;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final TransportationDemandBucketizer bucketizer;
    private final TransportationDemandPeriodBuilder periodBuilder;

    public TransportationDemandService(
            ScheduleProvider scheduleProvider,
            MorningDrivingPlanningService morningDrivingPlanningService,
            @Value("${app.transportation-demand.operating-start:09:00}") String operatingStart,
            @Value("${app.transportation-demand.midday-start:12:00}") String middayStart,
            @Value("${app.transportation-demand.evening-start:17:00}") String eveningStart,
            @Value("${app.transportation-demand.operating-end:21:00}") String operatingEnd,
            @Value("${app.transportation-demand.window-minutes:15}") int windowMinutes,
            @Value("${app.transportation-demand.buffer-minutes:15}") int bufferMinutes) {
        this.scheduleProvider = scheduleProvider;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.bucketizer = new TransportationDemandBucketizer(
                LocalTime.parse(operatingStart),
                LocalTime.parse(middayStart),
                LocalTime.parse(eveningStart),
                LocalTime.parse(operatingEnd),
                windowMinutes
        );
        this.periodBuilder = new TransportationDemandPeriodBuilder(
                windowMinutes,
                bufferMinutes,
                new DemandPriorityClassifier()
        );
    }

    public TransportationDemandAnalysis getDemandForDate(Long organizationId, LocalDate date) {
        List<ShiftRecord> shifts = scheduleProvider.getSchedulesForDate(organizationId, date);
        TransportationDemandBucketizer.Buckets buckets = bucketizer.bucket(shifts);
        MorningDrivingPlan morningDrivingPlan = morningDrivingPlanningService.getPlanForDate(organizationId, date);

        TransportationDemandPeriod morning = periodBuilder.buildPeriod(
                TransportationPeriodType.MORNING,
                buckets.forType(TransportationPeriodType.MORNING),
                morningDrivingPlan.hasDemand() ? morningDrivingPlan.getScheduledStartTime() : null,
                morningDrivingPlan.hasDemand() ? morningDrivingPlan.getScheduledEndTime() : null
        );

        List<TransportationDemandWindow> middayWindows = periodBuilder.buildWindows(
                TransportationPeriodType.MIDDAY,
                buckets.forType(TransportationPeriodType.MIDDAY)
        );
        TransportationDemandPeriod midday = periodBuilder.buildPeriodFromWindows(
                TransportationPeriodType.MIDDAY,
                middayWindows,
                periodBuilder.firstWindowStart(middayWindows),
                periodBuilder.lastWindowEnd(middayWindows)
        );

        List<TransportationDemandWindow> eveningWindows = periodBuilder.buildWindows(
                TransportationPeriodType.EVENING,
                buckets.forType(TransportationPeriodType.EVENING)
        );
        TransportationDemandPeriod evening = periodBuilder.buildPeriodFromWindows(
                TransportationPeriodType.EVENING,
                eveningWindows,
                periodBuilder.firstWindowStart(eveningWindows),
                periodBuilder.bufferedEnd(eveningWindows)
        );

        List<TransportationDemandWindow> offHoursWindows = periodBuilder.buildWindows(
                TransportationPeriodType.OFF_HOURS,
                buckets.forType(TransportationPeriodType.OFF_HOURS)
        );
        int offHoursMovementCount = offHoursWindows.stream()
                .mapToInt(TransportationDemandWindow::getDemandCount)
                .sum();

        TransportationDemandPeriod peakPeriod = List.of(morning, midday, evening).stream()
                .filter(TransportationDemandPeriod::hasDemand)
                .max(Comparator.comparingInt(TransportationDemandPeriod::getPeakWindowCount))
                .orElse(null);

        return new TransportationDemandAnalysis(
                morning,
                midday,
                evening,
                offHoursWindows,
                offHoursMovementCount,
                peakPeriod == null ? null : peakPeriod.getPeakWindowStart(),
                peakPeriod == null ? null : peakPeriod.getPeakWindowEnd(),
                peakPeriod == null ? 0 : peakPeriod.getPeakWindowCount(),
                peakPeriod == null ? null : peakPeriod.getTitle()
        );
    }
}
