package technology.renatus.routepulse.model;

import java.time.LocalTime;
import java.util.List;

/**
 * One major transportation behavior period shown on the planning dashboard.
 */
public class TransportationDemandPeriod {

    private final String key;
    private final String title;
    private final String summaryTitle;
    private final String description;
    private final String icon;
    private final String totalLabel;
    private final String peakLabel;
    private final List<TransportationDemandWindow> windows;
    private final LocalTime recommendedStartTime;
    private final LocalTime recommendedEndTime;
    private final int totalCount;
    private final LocalTime peakWindowStart;
    private final LocalTime peakWindowEnd;
    private final int peakWindowCount;

    public TransportationDemandPeriod(
            String key,
            String title,
            String summaryTitle,
            String description,
            String icon,
            String totalLabel,
            String peakLabel,
            List<TransportationDemandWindow> windows,
            LocalTime recommendedStartTime,
            LocalTime recommendedEndTime,
            int totalCount,
            LocalTime peakWindowStart,
            LocalTime peakWindowEnd,
            int peakWindowCount) {

        this.key = key;
        this.title = title;
        this.summaryTitle = summaryTitle;
        this.description = description;
        this.icon = icon;
        this.totalLabel = totalLabel;
        this.peakLabel = peakLabel;
        this.windows = List.copyOf(windows);
        this.recommendedStartTime = recommendedStartTime;
        this.recommendedEndTime = recommendedEndTime;
        this.totalCount = totalCount;
        this.peakWindowStart = peakWindowStart;
        this.peakWindowEnd = peakWindowEnd;
        this.peakWindowCount = peakWindowCount;
    }

    public static TransportationDemandPeriod empty(
            String key,
            String title,
            String summaryTitle,
            String description,
            String icon,
            String totalLabel,
            String peakLabel) {

        return new TransportationDemandPeriod(
                key,
                title,
                summaryTitle,
                description,
                icon,
                totalLabel,
                peakLabel,
                List.of(),
                null,
                null,
                0,
                null,
                null,
                0
        );
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getSummaryTitle() {
        return summaryTitle;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public String getTotalLabel() {
        return totalLabel;
    }

    public String getPeakLabel() {
        return peakLabel;
    }

    public List<TransportationDemandWindow> getWindows() {
        return windows;
    }

    public LocalTime getRecommendedStartTime() {
        return recommendedStartTime;
    }

    public LocalTime getRecommendedEndTime() {
        return recommendedEndTime;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public LocalTime getPeakWindowStart() {
        return peakWindowStart;
    }

    public LocalTime getPeakWindowEnd() {
        return peakWindowEnd;
    }

    public int getPeakWindowCount() {
        return peakWindowCount;
    }

    public boolean hasDemand() {
        return totalCount > 0 && !windows.isEmpty();
    }

    public boolean hasRecommendedWindow() {
        return recommendedStartTime != null
                && recommendedEndTime != null;
    }
}
