package technology.renatus.routepulse.scheduling.domain;

import java.time.LocalTime;

/**
 * One 15-minute transportation-demand window.
 *
 * Arrivals represent scheduled shift starts.
 * Departures represent scheduled shift endings.
 * Demand count is the operational count used by the owning period:
 *
 * - Morning Arrivals: arrivals
 * - Midday Turnover: arrivals + departures
 * - Evening Pickups: departures
 * - Off-hours: arrivals + departures
 */
public class TransportationDemandWindow {

    private final LocalTime windowStart;
    private final LocalTime windowEnd;
    private final int arrivals;
    private final int departures;
    private final int demandCount;
    private final PickupPriority priority;

    public TransportationDemandWindow(
            LocalTime windowStart,
            LocalTime windowEnd,
            int arrivals,
            int departures,
            int demandCount,
            PickupPriority priority) {

        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.arrivals = arrivals;
        this.departures = departures;
        this.demandCount = demandCount;
        this.priority = priority;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public int getArrivals() {
        return arrivals;
    }

    public int getDepartures() {
        return departures;
    }

    public int getDemandCount() {
        return demandCount;
    }

    public int getMovementCount() {
        return arrivals + departures;
    }

    public PickupPriority getPriority() {
        return priority;
    }

    public boolean hasArrivals() {
        return arrivals > 0;
    }

    public boolean hasDepartures() {
        return departures > 0;
    }
}
