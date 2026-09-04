package technology.renatus.routepulse.model;

import java.util.List;

public class PickupDemandSection {

    private final String name;
    private final String timeRange;
    private final String icon;
    private final List<PickupWindow> windows;
    private final boolean sustainedDemand;

    public PickupDemandSection(
            String name,
            String timeRange,
            String icon,
            List<PickupWindow> windows,
            boolean sustainedDemand) {

        this.name = name;
        this.timeRange = timeRange;
        this.icon = icon;
        this.windows = List.copyOf(windows);
        this.sustainedDemand = sustainedDemand;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {

        return sustainedDemand
                ? name + " rush"
                : name;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public String getIcon() {
        return icon;
    }

    public List<PickupWindow> getWindows() {
        return windows;
    }

    public boolean isSustainedDemand() {
        return sustainedDemand;
    }
}