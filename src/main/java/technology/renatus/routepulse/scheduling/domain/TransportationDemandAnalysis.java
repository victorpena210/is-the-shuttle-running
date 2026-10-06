package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalTime;
import java.util.List;

/**
 * Transportation-demand view model for a single operating date.
 *
 * Normal RoutePulse operations are presented as Morning Arrivals,
 * Midday Turnover, and Evening Pickups. Schedule activity outside the
 * configured operating day is retained but visually demoted to Off-hours.
 */
public class TransportationDemandAnalysis {

    private final List<TransportationDemandWindow> movementWindows;
    private final TransportationDemandPeriod morning;
    private final TransportationDemandPeriod midday;
    private final TransportationDemandPeriod evening;
    private final List<TransportationDemandWindow> offHoursWindows;
    private final int offHoursMovementCount;
    private final LocalTime overallPeakWindowStart;
    private final LocalTime overallPeakWindowEnd;
    private final int overallPeakWindowCount;
    private final String overallPeakPeriodTitle;

    public TransportationDemandAnalysis(
            TransportationDemandPeriod morning,
            TransportationDemandPeriod midday,
            TransportationDemandPeriod evening,
            List<TransportationDemandWindow> offHoursWindows,
            int offHoursMovementCount,
            LocalTime overallPeakWindowStart,
            LocalTime overallPeakWindowEnd,
            int overallPeakWindowCount,
            String overallPeakPeriodTitle) {

        this(morning, midday, evening, offHoursWindows, offHoursMovementCount, overallPeakWindowStart,
                overallPeakWindowEnd, overallPeakWindowCount, overallPeakPeriodTitle,
                java.util.stream.Stream.of(morning.getWindows(), midday.getWindows(), evening.getWindows(), offHoursWindows)
                        .flatMap(List::stream).toList());
    }

    public TransportationDemandAnalysis(TransportationDemandPeriod morning, TransportationDemandPeriod midday,
            TransportationDemandPeriod evening, List<TransportationDemandWindow> offHoursWindows, int offHoursMovementCount,
            LocalTime overallPeakWindowStart, LocalTime overallPeakWindowEnd, int overallPeakWindowCount,
            String overallPeakPeriodTitle, List<TransportationDemandWindow> movementWindows) {
        this.movementWindows = List.copyOf(movementWindows);
        this.morning = morning;
        this.midday = midday;
        this.evening = evening;
        this.offHoursWindows = List.copyOf(offHoursWindows);
        this.offHoursMovementCount = offHoursMovementCount;
        this.overallPeakWindowStart = overallPeakWindowStart;
        this.overallPeakWindowEnd = overallPeakWindowEnd;
        this.overallPeakWindowCount = overallPeakWindowCount;
        this.overallPeakPeriodTitle = overallPeakPeriodTitle;
    }

    public static TransportationDemandAnalysis empty() {

        return new TransportationDemandAnalysis(
                TransportationDemandPeriod.empty(
                        "morning",
                        "Morning Arrivals",
                        "Morning driving",
                        "Scheduled team-member clock-ins.",
                        "↑",
                        "arrivals",
                        "Peak arrival"
                ),
                TransportationDemandPeriod.empty(
                        "midday",
                        "Midday Turnover",
                        "Midday coverage",
                        "Clock-ins and clock-outs happening together.",
                        "↕",
                        "transitions",
                        "Peak turnover"
                ),
                TransportationDemandPeriod.empty(
                        "evening",
                        "Evening Pickups",
                        "Evening driving",
                        "Scheduled team-member clock-outs.",
                        "↓",
                        "pickups",
                        "Peak pickup"
                ),
                List.of(),
                0,
                null,
                null,
                0,
                null
        );
    }

    public List<TransportationDemandWindow> getMovementWindows() { return movementWindows; }

    public TransportationDemandPeriod getMorning() {
        return morning;
    }

    public TransportationDemandPeriod getMidday() {
        return midday;
    }

    public TransportationDemandPeriod getEvening() {
        return evening;
    }

    public List<TransportationDemandPeriod> getPeriods() {
        return List.of(morning, midday, evening);
    }

    public List<TransportationDemandWindow> getOffHoursWindows() {
        return offHoursWindows;
    }

    public int getOffHoursMovementCount() {
        return offHoursMovementCount;
    }

    public LocalTime getOverallPeakWindowStart() {
        return overallPeakWindowStart;
    }

    public LocalTime getOverallPeakWindowEnd() {
        return overallPeakWindowEnd;
    }

    public int getOverallPeakWindowCount() {
        return overallPeakWindowCount;
    }

    public String getOverallPeakPeriodTitle() {
        return overallPeakPeriodTitle;
    }

    public boolean hasOperationalDemand() {
        return morning.hasDemand()
                || midday.hasDemand()
                || evening.hasDemand();
    }

    public boolean hasOffHoursDemand() {
        return offHoursMovementCount > 0
                && !offHoursWindows.isEmpty();
    }

    public boolean hasAnyDemand() {
        return hasOperationalDemand() || hasOffHoursDemand();
    }
}
