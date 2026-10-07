package technology.renatus.routepulse.driver.availability;

import jakarta.persistence.*;
import java.time.*;
import technology.renatus.routepulse.driver.model.Driver;

/** Append-only audit; the newest revision for a driver/date determines availability. */
@Entity
@Table(name = "driver_availability_revisions")
public class DriverAvailabilityRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "driver_id") private Driver driver;
    @Column(name = "organization_id", nullable = false) private Long organizationId;
    @Column(name = "shift_date", nullable = false) private LocalDate shiftDate;
    @Column(name = "work_windows", nullable = false, length = 100) private String workWindows;
    @Column(name = "imported_windows", nullable = false, length = 1000) private String importedWindows;
    @Column(name = "previous_windows", nullable = false, length = 1000) private String previousWindows;
    @Column(name = "use_imported", nullable = false) private boolean useImported;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "source_digest", length = 200) private String sourceDigest;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    @Column(name = "confirmed_by", nullable = false, length = 254) private String confirmedBy;
    protected DriverAvailabilityRevision() { }
    public DriverAvailabilityRevision(Driver driver, LocalDate date, String windows, String imported,
            String previous, boolean useImported, String source, String digest, String actor) {
        this.driver = driver; this.organizationId = driver.getOrganization().getId(); this.shiftDate = date;
        this.workWindows = windows; this.importedWindows = imported; this.previousWindows = previous;
        this.useImported = useImported; this.source = source; this.sourceDigest = digest;
        this.confirmedAt = Instant.now(); this.confirmedBy = actor;
    }
    public Long getId() { return id; }
    public Driver getDriver() { return driver; }
    public Long getOrganizationId() { return organizationId; }
    public LocalDate getShiftDate() { return shiftDate; }
    public String getWorkWindows() { return workWindows; }
    public String getImportedWindows() { return importedWindows; }
    public String getPreviousWindows() { return previousWindows; }
    public boolean isUseImported() { return useImported; }
    public String getSource() { return source; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getConfirmedBy() { return confirmedBy; }
    public String getHoursLabel() { return AvailabilityWindows.label(workWindows); }
    public String getPreviousLabel() { return AvailabilityWindows.label(previousWindows); }
}
