package technology.renatus.routepulse.driver.calendar;

import jakarta.persistence.*;
import java.time.*;
import technology.renatus.routepulse.driver.model.Driver;

@Entity @Table(name="driver_calendar_connections")
public class DriverCalendarConnection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id",nullable=false) private Long organizationId;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="driver_id",nullable=false,unique=true) private Driver driver;
    @Column(name="encrypted_link",nullable=false,length=2048) private String encryptedLink;
    @Column(name="feed_fingerprint",nullable=false,length=64) private String feedFingerprint;
    @Version private long revision;
    @Column(name="connected_at",nullable=false) private Instant connectedAt;
    @Column(name="last_attempt_at",nullable=false) private Instant lastAttemptAt;
    @Column(name="last_synced_at",nullable=false) private Instant lastSyncedAt;
    @Column(name="next_sync_at",nullable=false) private Instant nextSyncAt;
    @Column(name="coverage_from",nullable=false) private LocalDate coverageFrom;
    @Column(name="coverage_until",nullable=false) private LocalDate coverageUntil;
    @Column(name="last_error",length=300) private String lastError;
    protected DriverCalendarConnection() { }
    public DriverCalendarConnection(Driver driver, String encryptedLink, String fingerprint, Instant now, ZoneId zone) {
        this.driver=driver; organizationId=driver.getOrganization().getId(); connectedAt=now;
        this.encryptedLink=encryptedLink; this.feedFingerprint=fingerprint; synced(now,zone);
    }
    public void reconnect(String encryptedLink, String fingerprint, Instant now, ZoneId zone) { this.encryptedLink=encryptedLink; feedFingerprint=fingerprint; connectedAt=now; synced(now,zone); }
    public void synced(Instant now, ZoneId zone) {
        lastAttemptAt=now; lastSyncedAt=now; nextSyncAt=now.plusSeconds(900); lastError=null;
        coverageFrom=now.atZone(zone).toLocalDate(); coverageUntil=coverageFrom.plusWeeks(6);
    }
    public void failed(Instant now, String error) { lastAttemptAt=now; nextSyncAt=now.plusSeconds(900); lastError=error; }
    public boolean fresh(Instant now) { return lastSyncedAt.isAfter(now.minus(Duration.ofHours(24))); }
    public Long getId() { return id; } public Long getOrganizationId() { return organizationId; }
    public Driver getDriver() { return driver; } public String getEncryptedLink() { return encryptedLink; }
    public long getRevision() { return revision; } public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; } public Instant getNextSyncAt() { return nextSyncAt; }
    public LocalDate getCoverageFrom() { return coverageFrom; } public LocalDate getCoverageUntil() { return coverageUntil; }
    public String getLastError() { return lastError; }
}
