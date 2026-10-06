package technology.renatus.routepulse.scheduling.planning.transportation;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;
import technology.renatus.routepulse.scheduling.planning.common.DemandPriorityClassifier;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationDemandBucketizer.WindowCounts;

/** Builds demand windows and summarized periods from bucketed schedule activity. */
public class TransportationDemandPeriodBuilder {

    private final int windowMinutes;
    private final int bufferMinutes;
    private final DemandPriorityClassifier priorityClassifier;

    public TransportationDemandPeriodBuilder(
            int windowMinutes,
            int bufferMinutes,
            DemandPriorityClassifier priorityClassifier) {
        if (bufferMinutes < 0) {
            throw new IllegalArgumentException("Transportation-demand buffer minutes cannot be negative.");
        }
        this.windowMinutes = windowMinutes;
        this.bufferMinutes = bufferMinutes;
        this.priorityClassifier = priorityClassifier;
    }

    public TransportationDemandPeriod buildPeriod(
            TransportationPeriodType type,
            Map<LocalTime, WindowCounts> buckets,
            LocalTime recommendedStart,
            LocalTime recommendedEnd) {
        return buildPeriodFromWindows(
                type,
                buildWindows(type, buckets),
                recommendedStart,
                recommendedEnd
        );
    }

    public TransportationDemandPeriod buildPeriodFromWindows(
            TransportationPeriodType type,
            List<TransportationDemandWindow> windows,
            LocalTime recommendedStart,
            LocalTime recommendedEnd) {
        int totalCount = windows.stream()
                .mapToInt(TransportationDemandWindow::getDemandCount)
                .sum();
        TransportationDemandWindow peak = windows.stream()
                .max(Comparator.comparingInt(TransportationDemandWindow::getDemandCount))
                .orElse(null);

        return new TransportationDemandPeriod(
                type.key(),
                type.title(),
                type.summaryTitle(),
                type.description(),
                type.icon(),
                type.totalLabel(),
                type.peakLabel(),
                windows,
                recommendedStart,
                recommendedEnd,
                totalCount,
                peak == null ? null : peak.getWindowStart(),
                peak == null ? null : peak.getWindowEnd(),
                peak == null ? 0 : peak.getDemandCount()
        );
    }

    public List<TransportationDemandWindow> buildWindows(
            TransportationPeriodType type,
            Map<LocalTime, WindowCounts> buckets) {
        List<TransportationDemandWindow> windows = new ArrayList<>();

        for (Map.Entry<LocalTime, WindowCounts> entry : buckets.entrySet()) {
            WindowCounts counts = entry.getValue();
            int demandCount = switch (type) {
                case MORNING -> counts.arrivals();
                case MIDDAY -> counts.arrivals() + counts.departures();
                case EVENING -> counts.departures();
                case OFF_HOURS -> counts.arrivals() + counts.departures();
            };

            // Morning focuses on arrivals; Evening focuses on pickups.
            if (demandCount <= 0) {
                continue;
            }

            LocalTime windowStart = entry.getKey();
            LocalTime windowEnd = sameDayEnd(windowStart, windowMinutes);
            if (!windowEnd.isAfter(windowStart)) windowEnd = LocalTime.MAX;
            windows.add(new TransportationDemandWindow(
                    windowStart,
                    windowEnd,
                    counts.arrivals(),
                    counts.departures(),
                    demandCount,
                    priorityClassifier.classify(demandCount)
            ));
        }

        return List.copyOf(windows);
    }

    public LocalTime firstWindowStart(List<TransportationDemandWindow> windows) {
        return windows.isEmpty() ? null : windows.get(0).getWindowStart();
    }

    public LocalTime lastWindowEnd(List<TransportationDemandWindow> windows) {
        return windows.isEmpty() ? null : windows.get(windows.size() - 1).getWindowEnd();
    }

    /** LocalTime arithmetic wraps at midnight; same-day demand must keep a later end time. */
    private LocalTime sameDayEnd(LocalTime start, int extraMinutes) {
        long endMinute = start.toSecondOfDay() / 60L + extraMinutes;
        return LocalTime.ofSecondOfDay(Math.min(endMinute, 1439) * 60L);
    }

    public LocalTime bufferedStart(List<TransportationDemandWindow> windows) {
        LocalTime first = firstWindowStart(windows);
        return first == null ? null : first.minusMinutes(bufferMinutes);
    }

    public LocalTime bufferedEnd(List<TransportationDemandWindow> windows) {
        LocalTime last = lastWindowEnd(windows);
        return last == null ? null : sameDayEnd(last, bufferMinutes);
    }
}
