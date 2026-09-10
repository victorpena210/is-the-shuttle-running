package technology.renatus.routepulse.scheduling.planning.transportation;

/** Describes the manager-facing transportation sections and how their windows are counted. */
public enum TransportationPeriodType {

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
            "Sporadic clock-ins and clock-outs; the assigned driver follows specific start/stop windows.",
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

    TransportationPeriodType(
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

    public String key() { return key; }
    public String title() { return title; }
    public String summaryTitle() { return summaryTitle; }
    public String description() { return description; }
    public String icon() { return icon; }
    public String totalLabel() { return totalLabel; }
    public String peakLabel() { return peakLabel; }
}
