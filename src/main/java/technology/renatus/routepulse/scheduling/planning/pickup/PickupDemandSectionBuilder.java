package technology.renatus.routepulse.scheduling.planning.pickup;

import java.util.ArrayList;
import java.util.List;
import technology.renatus.routepulse.scheduling.domain.PickupDemandSection;
import technology.renatus.routepulse.scheduling.domain.PickupWindow;

/** Groups pickup windows into readable daypart sections for the dashboard. */
public class PickupDemandSectionBuilder {

    public List<PickupDemandSection> build(List<PickupWindow> windows) {
        List<PickupDemandSection> sections = new ArrayList<>();
        addSection(sections, "Overnight", "12:00 AM – 5:59 AM", "☾", windowsInHourRange(windows, 0, 6));
        addSection(sections, "Morning", "6:00 AM – 11:59 AM", "☀", windowsInHourRange(windows, 6, 12));
        addSection(sections, "Afternoon", "12:00 PM – 5:59 PM", "◐", windowsInHourRange(windows, 12, 18));
        addSection(sections, "Evening", "6:00 PM – 11:59 PM", "☾", windowsInHourRange(windows, 18, 24));
        return List.copyOf(sections);
    }

    private List<PickupWindow> windowsInHourRange(
            List<PickupWindow> windows,
            int startHourInclusive,
            int endHourExclusive) {
        return windows.stream()
                .filter(window -> {
                    int hour = window.getWindowStart().getHour();
                    return hour >= startHourInclusive && hour < endHourExclusive;
                })
                .toList();
    }

    private void addSection(
            List<PickupDemandSection> sections,
            String name,
            String timeRange,
            String icon,
            List<PickupWindow> windows) {
        if (windows.isEmpty()) {
            return;
        }

        boolean sustainedDemand = windows.stream().anyMatch(PickupWindow::isSustainedDemand);
        sections.add(new PickupDemandSection(name, timeRange, icon, windows, sustainedDemand));
    }
}
