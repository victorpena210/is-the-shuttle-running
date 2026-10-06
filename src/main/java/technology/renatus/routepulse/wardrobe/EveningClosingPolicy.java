package technology.renatus.routepulse.wardrobe;

import java.time.LocalTime;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

/** Limits assignment windows while preserving the original transportation forecast elsewhere. */
public final class EveningClosingPolicy {
    private EveningClosingPolicy() {}
    public static TransportationDemandPeriod afterClosing(TransportationDemandPeriod demand, LocalTime closingTime) {
        // Midnight is the end of the selected office day, so no same-day evening run can start.
        if (closingTime.equals(LocalTime.MIDNIGHT)) return empty(demand);
        return notBefore(demand, closingTime);
    }
    public static TransportationDemandPeriod notBefore(TransportationDemandPeriod demand, LocalTime earliest) {
        if (!demand.hasRecommendedWindow()) return empty(demand);
        var windows = demand.getWindows().stream().filter(w -> w.getDemandCount() > 0 && w.getWindowEnd().isAfter(earliest))
                .map(w -> new TransportationDemandWindow(w.getWindowStart().isBefore(earliest) ? earliest : w.getWindowStart(),
                        w.getWindowEnd(), w.getArrivals(), w.getDepartures(), w.getDemandCount(), w.getPriority())).toList();
        if (windows.isEmpty() || !demand.getRecommendedEndTime().isAfter(earliest)) return empty(demand);
        LocalTime start = demand.getRecommendedStartTime().isBefore(earliest) ? earliest : demand.getRecommendedStartTime();
        var peak = windows.stream().max(java.util.Comparator.comparingInt(TransportationDemandWindow::getDemandCount)).orElseThrow();
        return new TransportationDemandPeriod(demand.getKey(), demand.getTitle(), demand.getSummaryTitle(), demand.getDescription(),
                demand.getIcon(), demand.getTotalLabel(), demand.getPeakLabel(), windows, start, demand.getRecommendedEndTime(),
                windows.stream().mapToInt(TransportationDemandWindow::getDemandCount).sum(), peak.getWindowStart(), peak.getWindowEnd(), peak.getDemandCount());
    }
    private static TransportationDemandPeriod empty(TransportationDemandPeriod demand) {
        return TransportationDemandPeriod.empty(demand.getKey(), demand.getTitle(), demand.getSummaryTitle(), demand.getDescription(),
                demand.getIcon(), demand.getTotalLabel(), demand.getPeakLabel());
    }
}
