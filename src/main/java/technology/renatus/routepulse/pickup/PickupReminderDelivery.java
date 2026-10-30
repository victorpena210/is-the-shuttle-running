package technology.renatus.routepulse.pickup;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="pickup_reminder_deliveries")
public class PickupReminderDelivery {
    public enum Status { SENDING, SENT, FAILED, UNCERTAIN, CANCELLED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id",nullable=false) private Long organizationId;
    @Column(name="rider_id",nullable=false) private Long riderId;
    @Column(name="clock_out",nullable=false) private Instant clockOut;
    @Column(name="driver_id",nullable=false) private Long driverId;
    @Enumerated(EnumType.STRING) @Column(name="delivery_status",nullable=false,length=20) private Status status;
    @Column(nullable=false) private int attempts;
    @Column(name="last_attempt_at",nullable=false) private Instant lastAttemptAt;
    @Column(name="sent_at") private Instant sentAt;
    protected PickupReminderDelivery() { }
    PickupReminderDelivery(Long org,Long rider,Instant time,Long driver) { organizationId=org;riderId=rider;clockOut=time;driverId=driver; }
    boolean canAttempt(Instant now) { return status==null || (status==Status.FAILED || status==Status.CANCELLED) && attempts<3 && !now.isBefore(lastAttemptAt.plusSeconds(60)); }
    void claim(Instant now) { status=Status.SENDING;attempts++;lastAttemptAt=now; }
    void sent(Instant now) { status=Status.SENT;sentAt=now; }
    void failed(boolean definitelyNotSent) { status=definitelyNotSent?Status.FAILED:Status.UNCERTAIN; }
    void cancel() { status=Status.CANCELLED; }
    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getRiderId() { return riderId; }
    public Instant getClockOut() { return clockOut; }
    public Long getDriverId() { return driverId; }
    public Status getStatus() { return status; }
}
