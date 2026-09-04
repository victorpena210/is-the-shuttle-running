package technology.renatus.routepulse.model;

import java.time.LocalTime;
import java.util.List;

public class PickupDemandAnalysis {

    private final List<PickupWindow> windows;

    private final List<PickupDemandSection>
            sections;

    private final LocalTime peakDemandStart;

    private final LocalTime peakDemandEnd;

    private final int peakDemandTeamMemberCount;

    private final int peakWindowTeamMemberCount;

    private final int sustainedWindowCount;

    public PickupDemandAnalysis(
            List<PickupWindow> windows,
            List<PickupDemandSection> sections,
            LocalTime peakDemandStart,
            LocalTime peakDemandEnd,
            int peakDemandTeamMemberCount,
            int peakWindowTeamMemberCount,
            int sustainedWindowCount) {

        this.windows =
                List.copyOf(windows);

        this.sections =
                List.copyOf(sections);

        this.peakDemandStart =
                peakDemandStart;

        this.peakDemandEnd =
                peakDemandEnd;

        this.peakDemandTeamMemberCount =
                peakDemandTeamMemberCount;

        this.peakWindowTeamMemberCount =
                peakWindowTeamMemberCount;

        this.sustainedWindowCount =
                sustainedWindowCount;
    }

    public static PickupDemandAnalysis empty() {

        return new PickupDemandAnalysis(
                List.of(),
                List.of(),
                null,
                null,
                0,
                0,
                0
        );
    }

    public List<PickupWindow> getWindows() {
        return windows;
    }

    public List<PickupDemandSection> getSections() {
        return sections;
    }

    public LocalTime getPeakDemandStart() {
        return peakDemandStart;
    }

    public LocalTime getPeakDemandEnd() {
        return peakDemandEnd;
    }

    public int getPeakDemandTeamMemberCount() {
        return peakDemandTeamMemberCount;
    }

    public int getPeakWindowTeamMemberCount() {
        return peakWindowTeamMemberCount;
    }

    public int getSustainedWindowCount() {
        return sustainedWindowCount;
    }

    public boolean isSustainedDemandDetected() {

        return peakDemandStart != null
                && peakDemandEnd != null
                && sustainedWindowCount >= 3;
    }
}