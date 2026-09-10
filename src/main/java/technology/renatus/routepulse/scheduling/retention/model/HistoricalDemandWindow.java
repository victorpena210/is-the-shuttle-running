package technology.renatus.routepulse.scheduling.retention.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;

/** Anonymous 15-minute arrival/departure counts for one historical day. */
@Entity
@Table(
        name = "historical_demand_windows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_historical_demand_window",
                columnNames = {"snapshot_id", "window_start"}
        )
)
public class HistoricalDemandWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false, updatable = false)
    private HistoricalDemandSnapshot snapshot;

    @Column(name = "window_start", nullable = false, updatable = false)
    private LocalTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalTime windowEnd;

    @Column(nullable = false)
    private int arrivals;

    @Column(nullable = false)
    private int departures;

    protected HistoricalDemandWindow() {
    }

    public HistoricalDemandWindow(
            LocalTime windowStart,
            LocalTime windowEnd,
            int arrivals,
            int departures) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.arrivals = arrivals;
        this.departures = departures;
    }

    void attachTo(HistoricalDemandSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    void refresh(
            LocalTime windowEnd,
            int arrivals,
            int departures) {
        this.windowEnd = windowEnd;
        this.arrivals = arrivals;
        this.departures = departures;
    }

    public Long getId() { return id; }
    public HistoricalDemandSnapshot getSnapshot() { return snapshot; }
    public LocalTime getWindowStart() { return windowStart; }
    public LocalTime getWindowEnd() { return windowEnd; }
    public int getArrivals() { return arrivals; }
    public int getDepartures() { return departures; }
    public int getMovementCount() { return arrivals + departures; }
}
