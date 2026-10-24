package technology.renatus.routepulse.scheduling.demand;

import jakarta.persistence.*;
import java.time.*;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.transportation.TransportationPeriodType;

/** Aggregate counts only; employee names and IDs are never stored in this forecast. */
@Entity
@Table(name = "transportation_demand_windows")
public class DemandForecastWindow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false) private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_import_id", nullable = false) private ScheduleImport sourceImport;
    @Column(name = "source_date", nullable = false) private LocalDate sourceDate;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TransportationPeriodType period;
    @Column(name = "window_start", nullable = false) private LocalTime windowStart;
    @Column(name = "clock_ins", nullable = false) private int clockIns;
    @Column(name = "clock_outs", nullable = false) private int clockOuts;
    protected DemandForecastWindow() {}
    public DemandForecastWindow(Organization organization, ScheduleImport sourceImport, LocalDate sourceDate,
            LocalDate eventDate, TransportationPeriodType period, LocalTime start, int ins, int outs) {
        this.organization = organization; this.sourceImport = sourceImport; this.sourceDate = sourceDate;
        this.eventDate = eventDate; this.period = period; windowStart = start; clockIns = ins; clockOuts = outs;
    }
    public LocalDate getEventDate() { return eventDate; }
    public TransportationPeriodType getPeriod() { return period; }
    public LocalTime getWindowStart() { return windowStart; }
    public int getClockIns() { return clockIns; }
    public int getClockOuts() { return clockOuts; }
    public ScheduleImport getSourceImport() { return sourceImport; }
}
