package technology.renatus.routepulse.scheduling.demand;

import jakarta.persistence.*;
import java.time.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

/** A registered driver's uploaded work hours, separate from actual driving. */
@Entity
@Table(name = "driver_schedule_windows")
public class DriverScheduleWindow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organization_id", nullable = false) private Long organizationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false) private Driver driver;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_import_id", nullable = false) private ScheduleImport sourceImport;
    @Column(name = "source_date", nullable = false) private LocalDate sourceDate;
    @Column(name = "starts_at", nullable = false) private LocalDateTime startsAt;
    @Column(name = "ends_at", nullable = false) private LocalDateTime endsAt;
    protected DriverScheduleWindow() { }
    public DriverScheduleWindow(Driver driver, ScheduleImport source, LocalDateTime start, LocalDateTime end) {
        this.driver = driver; organizationId = driver.getOrganization().getId(); sourceImport = source;
        sourceDate = start.toLocalDate(); startsAt = start; endsAt = end;
    }
    public Driver getDriver() { return driver; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
}
