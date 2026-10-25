package technology.renatus.routepulse.pickup;

import jakarta.persistence.*;
import java.time.*;

/** Operational pickup details only; no diagnoses or medical notes. */
@Entity @Table(name="pickup_riders")
public class PickupRider {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id",nullable=false) private Long organizationId;
    @Column(nullable=false,length=120) private String name;
    @Column(name="location_override",nullable=false,length=200) private String locationOverride;
    @Column(nullable=false) private boolean active=true;
    @Column(name="encrypted_link",nullable=false,length=2048) private String encryptedLink="";
    @Column(name="feed_fingerprint",nullable=false,length=64) private String feedFingerprint;
    @Version private long revision;
    @Column(name="last_attempt_at",nullable=false) private Instant lastAttemptAt;
    @Column(name="last_synced_at",nullable=false) private Instant lastSyncedAt;
    @Column(name="next_sync_at",nullable=false) private Instant nextSyncAt;
    @Column(name="last_error",length=300) private String lastError;
    protected PickupRider() { }
    public PickupRider(Long org,String name,String location,String fingerprint,Instant now) {
        this.organizationId=org;this.name=name;this.locationOverride=location;this.feedFingerprint=fingerprint;synced(now);
    }
    public void details(String name,String location) {this.name=name;this.locationOverride=location;}
    public void connect(String encrypted,String fingerprint,Instant now) {encryptedLink=encrypted;feedFingerprint=fingerprint;synced(now);}
    public void synced(Instant now) {lastAttemptAt=now;lastSyncedAt=now;nextSyncAt=now.plusSeconds(900);lastError=null;}
    public void failed(Instant now) {lastAttemptAt=now;nextSyncAt=now.plusSeconds(900);lastError="Calendar refresh failed. Showing the last saved schedule; confirm with the rider.";}
    public void active(boolean value,Instant now) {active=value;nextSyncAt=now;}
    public Long getId(){return id;} public Long getOrganizationId(){return organizationId;}
    public String getName(){return name;} public String getLocationOverride(){return locationOverride;}
    public boolean isActive(){return active;} public String getEncryptedLink(){return encryptedLink;}
    public long getRevision(){return revision;} public Instant getLastAttemptAt(){return lastAttemptAt;}
    public Instant getLastSyncedAt(){return lastSyncedAt;} public String getLastError(){return lastError;}
    public boolean stale(Instant now){return lastError!=null || lastSyncedAt.isBefore(now.minusSeconds(86400));}
}
