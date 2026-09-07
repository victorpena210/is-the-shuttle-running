package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.MorningDrivingPlan;
import technology.renatus.routepulse.model.PickupPriority;
import technology.renatus.routepulse.model.ShiftRecord;
import technology.renatus.routepulse.model.TransportationDemandAnalysis;
import technology.renatus.routepulse.model.TransportationDemandPeriod;
import technology.renatus.routepulse.model.TransportationDemandWindow;
import technology.renatus.routepulse.provider.ScheduleProvider;

/**
 * Builds the manager-facing transportation plan from uploaded schedules.
 *
 * This service determines WHERE demand exists in the operating day. It does
 * not choose drivers and does not modify fairness history. Driver assignment
 * remains owned by DriverAssignmentService / DriverWorkloadService.
 */
@Service
public class TransportationDemandService {

    private static final int MODERATE_DEMAND_MIN = 10;
    private static final int HIGH_DEMAND_MIN = 25;
    private static final int VERY_HIGH_DEMAND_MIN = 50;

    private final ScheduleProvider scheduleProvider;
    private final MorningDrivingPlanningService morningDrivingPlanningService;
    private final LocalTime operatingStart;
    private final LocalTime middayStart;
    private final LocalTime eveningStart;
    private final LocalTime operatingEnd;
    private final int windowMinutes;
    private final int bufferMinutes;

    public TransportationDemandService(
            ScheduleProvider scheduleProvider,
            MorningDrivingPlanningService morningDrivingPlanningService,
            @Value("${app.transportation-demand.operating-start:09:00}")
            String operatingStart,
            @Value("${app.transportation-demand.midday-start:12:00}")
            String middayStart,
            @Value("${app.transportation-demand.evening-start:17:00}")
            String eveningStart,
            @Value("${app.transportation-demand.operating-end:21:00}")
            String operatingEnd,
            @Value("${app.transportation-demand.window-minutes:15}")
            int windowMinutes,
            @Value("${app.transportation-demand.buffer-minutes:15}")
            int bufferMinutes) {

        this.scheduleProvider = scheduleProvider;
        this.morningDrivingPlanningService = morningDrivingPlanningService;
        this.operatingStart = LocalTime.parse(operatingStart);
        this.middayStart = LocalTime.parse(middayStart);
        this.eveningStart = LocalTime.parse(eveningStart);
        this.operatingEnd = LocalTime.parse(operatingEnd);

        if (windowMinutes <= 0 || 60 % windowMinutes != 0) {
            throw new IllegalArgumentException(
                    "Transportation-demand window minutes must be a positive divisor of 60."
            );
        }

        if (bufferMinutes < 0) {
            throw new IllegalArgumentException(
                    "Transportation-demand buffer minutes cannot be negative."
            );
        }

        if (!this.operatingStart.isBefore(this.middayStart)
                || !this.middayStart.isBefore(this.eveningStart)
                || !this.eveningStart.isBefore(this.operatingEnd)) {

            throw new IllegalArgumentException(
                    "Transportation-demand day boundaries must be ordered: "
                            + "operating start < midday start < evening start < operating end."
            );
        }

        this.windowMinutes = windowMinutes;
        this.bufferMinutes = bufferMinutes;
    }

    public TransportationDemandAnalysis getDemandForDate(
            Long organizationId,
            LocalDate date) {

        List<ShiftRecord> shifts = scheduleProvider
                .getSchedulesForDate(
                        organizationId,
                        date
                );

        Map<LocalTime, WindowCounts> morningBuckets = new TreeMap<>();
        Map<LocalTime, WindowCounts> middayBuckets = new TreeMap<>();
        Map<LocalTime, WindowCounts> eveningBuckets = new TreeMap<>();
        Map<LocalTime, WindowCounts> offHoursBuckets = new TreeMap<>();

        for (ShiftRecord shift : shifts) {

            addEvent(
                    shift.getStartTime(),
                    true,
                    morningBuckets,
                    middayBuckets,
                    eveningBuckets,
                    offHoursBuckets
            );

            addEvent(
                    shift.getEndTime(),
                    false,
                    morningBuckets,
                    middayBuckets,
                    eveningBuckets,
                    offHoursBuckets
            );
        }

        MorningDrivingPlan morningDrivingPlan =
                morningDrivingPlanningService
                        .getPlanForDate(
                                organizationId,
                                date
                        );

        TransportationDemandPeriod morning =
                buildPeriod(
                        PeriodType.MORNING,
                        morningBuckets,
                        morningDrivingPlan.hasDemand()
                                ? morningDrivingPlan.getScheduledStartTime()
                                : null,
                        morningDrivingPlan.hasDemand()
                                ? morningDrivingPlan.getScheduledEndTime()
                                : null
                );

        List<TransportationDemandWindow> middayWindows =
                buildWindows(
                        PeriodType.MIDDAY,
                        middayBuckets
                );

        TransportationDemandPeriod midday =
                buildPeriodFromWindows(
                        PeriodType.MIDDAY,
                        middayWindows,
                        firstWindowStart(middayWindows),
                        lastWindowEnd(middayWindows)
                );

        List<TransportationDemandWindow> eveningWindows =
                buildWindows(
                        PeriodType.EVENING,
                        eveningBuckets
                );

        TransportationDemandPeriod evening =
                buildPeriodFromWindows(
                        PeriodType.EVENING,
                        eveningWindows,
                        bufferedStart(eveningWindows),
                        bufferedEnd(eveningWindows)
                );

        List<TransportationDemandWindow> offHoursWindows =
                buildWindows(
                        PeriodType.OFF_HOURS,
                        offHoursBuckets
                );

        int offHoursMovementCount = offHoursWindows
                .stream()
                .mapToInt(TransportationDemandWindow::getDemandCount)
                .sum();

        List<TransportationDemandPeriod> operationalPeriods =
                List.of(
                        morning,
                        midday,
                        evening
                );

        TransportationDemandPeriod peakPeriod =
                operationalPeriods
                        .stream()
                        .filter(TransportationDemandPeriod::hasDemand)
                        .max(
                                Comparator.comparingInt(
                                        TransportationDemandPeriod::getPeakWindowCount
                                )
                        )
                        .orElse(null);

        return new TransportationDemandAnalysis(
                morning,
                midday,
                evening,
                offHoursWindows,
                offHoursMovementCount,
                peakPeriod == null
                        ? null
                        : peakPeriod.getPeakWindowStart(),
                peakPeriod == null
                        ? null
                        : peakPeriod.getPeakWindowEnd(),
                peakPeriod == null
                        ? 0
                        : peakPeriod.getPeakWindowCount(),
                peakPeriod == null
                        ? null
                        : peakPeriod.getTitle()
        );
    }

    private void addEvent(
            LocalTime eventTime,
            boolean arrival,
            Map<LocalTime, WindowCounts> morningBuckets,
            Map<LocalTime, WindowCounts> middayBuckets,
            Map<LocalTime, WindowCounts> eveningBuckets,
            Map<LocalTime, WindowCounts> offHoursBuckets) {

        if (eventTime == null) {
            return;
        }

        Map<LocalTime, WindowCounts> target =
                selectBucket(
                        eventTime,
                        arrival,
                        morningBuckets,
                        middayBuckets,
                        eveningBuckets,
                        offHoursBuckets
                );

        LocalTime windowStart =
                floorToWindow(eventTime);

        WindowCounts counts =
                target.computeIfAbsent(
                        windowStart,
                        ignored -> new WindowCounts()
                );

        if (arrival) {
            counts.arrivals++;
        } else {
            counts.departures++;
        }
    }

    private Map<LocalTime, WindowCounts> selectBucket(
            LocalTime eventTime,
            boolean arrival,
            Map<LocalTime, WindowCounts> morningBuckets,
            Map<LocalTime, WindowCounts> middayBuckets,
            Map<LocalTime, WindowCounts> eveningBuckets,
            Map<LocalTime, WindowCounts> offHoursBuckets) {

        /*
         * 9:00 AM through exactly 9:00 PM is normal operations.
         * A 9:00 PM clock-out therefore remains an Evening Pickup.
         * Anything after 9:00 PM is off-hours.
         */
        if (eventTime.isBefore(operatingStart)
                || eventTime.isAfter(operatingEnd)) {

            return offHoursBuckets;
        }

        /*
         * MorningDrivingPlanningService intentionally includes clock-ins at
         * exactly noon. Keep the dashboard aligned with the generated MORNING
         * assignment by treating a noon arrival as Morning Arrivals too.
         * Noon departures still belong to Midday Turnover.
         */
        if (eventTime.isBefore(middayStart)
                || (arrival && eventTime.equals(middayStart))) {
            return morningBuckets;
        }

        if (eventTime.isBefore(eveningStart)) {
            return middayBuckets;
        }

        return eveningBuckets;
    }

    private TransportationDemandPeriod buildPeriod(
            PeriodType type,
            Map<LocalTime, WindowCounts> buckets,
            LocalTime recommendedStart,
            LocalTime recommendedEnd) {

        return buildPeriodFromWindows(
                type,
                buildWindows(
                        type,
                        buckets
                ),
                recommendedStart,
                recommendedEnd
        );
    }

    private TransportationDemandPeriod buildPeriodFromWindows(
            PeriodType type,
            List<TransportationDemandWindow> windows,
            LocalTime recommendedStart,
            LocalTime recommendedEnd) {

        int totalCount = windows
                .stream()
                .mapToInt(TransportationDemandWindow::getDemandCount)
                .sum();

        TransportationDemandWindow peak = windows
                .stream()
                .max(
                        Comparator.comparingInt(
                                TransportationDemandWindow::getDemandCount
                        )
                )
                .orElse(null);

        return new TransportationDemandPeriod(
                type.key,
                type.title,
                type.summaryTitle,
                type.description,
                type.icon,
                type.totalLabel,
                type.peakLabel,
                windows,
                recommendedStart,
                recommendedEnd,
                totalCount,
                peak == null ? null : peak.getWindowStart(),
                peak == null ? null : peak.getWindowEnd(),
                peak == null ? 0 : peak.getDemandCount()
        );
    }

    private List<TransportationDemandWindow> buildWindows(
            PeriodType type,
            Map<LocalTime, WindowCounts> buckets) {

        List<TransportationDemandWindow> windows =
                new ArrayList<>();

        for (Map.Entry<LocalTime, WindowCounts> entry :
                buckets.entrySet()) {

            WindowCounts counts = entry.getValue();

            int demandCount = switch (type) {
                case MORNING -> counts.arrivals;
                case MIDDAY -> counts.arrivals + counts.departures;
                case EVENING -> counts.departures;
                case OFF_HOURS -> counts.arrivals + counts.departures;
            };

            /*
             * Morning intentionally focuses on arrivals and Evening
             * intentionally focuses on pickups. Do not create a row when
             * the period's primary behavior has no demand in that window.
             */
            if (demandCount <= 0) {
                continue;
            }

            LocalTime windowStart = entry.getKey();

            windows.add(
                    new TransportationDemandWindow(
                            windowStart,
                            windowStart.plusMinutes(windowMinutes),
                            counts.arrivals,
                            counts.departures,
                            demandCount,
                            determinePriority(demandCount)
                    )
            );
        }

        return List.copyOf(windows);
    }

    private LocalTime firstWindowStart(
            List<TransportationDemandWindow> windows) {

        if (windows.isEmpty()) {
            return null;
        }

        return windows.get(0).getWindowStart();
    }

    private LocalTime lastWindowEnd(
            List<TransportationDemandWindow> windows) {

        if (windows.isEmpty()) {
            return null;
        }

        return windows
                .get(windows.size() - 1)
                .getWindowEnd();
    }

    private LocalTime bufferedStart(
            List<TransportationDemandWindow> windows) {

        LocalTime first = firstWindowStart(windows);

        return first == null
                ? null
                : first.minusMinutes(bufferMinutes);
    }

    private LocalTime bufferedEnd(
            List<TransportationDemandWindow> windows) {

        LocalTime last = lastWindowEnd(windows);

        return last == null
                ? null
                : last.plusMinutes(bufferMinutes);
    }

    private LocalTime floorToWindow(
            LocalTime time) {

        int roundedMinute =
                (time.getMinute() / windowMinutes)
                        * windowMinutes;

        return time
                .withMinute(roundedMinute)
                .withSecond(0)
                .withNano(0);
    }

    private PickupPriority determinePriority(
            int count) {

        if (count >= VERY_HIGH_DEMAND_MIN) {
            return PickupPriority.VERY_HIGH;
        }

        if (count >= HIGH_DEMAND_MIN) {
            return PickupPriority.HIGH;
        }

        if (count >= MODERATE_DEMAND_MIN) {
            return PickupPriority.MEDIUM;
        }

        return PickupPriority.LOW;
    }

    private static class WindowCounts {
        private int arrivals;
        private int departures;
    }

    private enum PeriodType {

        MORNING(
                "morning",
                "Morning Arrivals",
                "Morning driving",
                "Team members clocking in and moving toward the park.",
                "↑",
                "arrivals",
                "Peak arrival"
        ),

        MIDDAY(
                "midday",
                "Midday Turnover",
                "Midday coverage",
                "Clock-ins and clock-outs happening during the same part of the day.",
                "↕",
                "transitions",
                "Peak turnover"
        ),

        EVENING(
                "evening",
                "Evening Pickups",
                "Evening driving",
                "Team members clocking out and moving away from the park.",
                "↓",
                "pickups",
                "Peak pickup"
        ),

        OFF_HOURS(
                "off-hours",
                "Off-hours Demand",
                "Off-hours demand",
                "Schedule activity outside normal 9:00 AM–9:00 PM operations.",
                "·",
                "movements",
                "Peak off-hours movement"
        );

        private final String key;
        private final String title;
        private final String summaryTitle;
        private final String description;
        private final String icon;
        private final String totalLabel;
        private final String peakLabel;

        PeriodType(
                String key,
                String title,
                String summaryTitle,
                String description,
                String icon,
                String totalLabel,
                String peakLabel) {

            this.key = key;
            this.title = title;
            this.summaryTitle = summaryTitle;
            this.description = description;
            this.icon = icon;
            this.totalLabel = totalLabel;
            this.peakLabel = peakLabel;
        }
    }
}
