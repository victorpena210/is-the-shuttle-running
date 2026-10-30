package technology.renatus.routepulse.pickup;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="pickup_outcomes")
public class PickupOutcome {
    public enum Status {
        PENDING("Pickup unconfirmed"), PICKED_UP("Picked up"), NOT_NEEDED("No pickup needed");
        private final String label;
        Status(String label) { this.label=label; }
        public String getLabel() { return label; }
    }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id",nullable=false) private Long organizationId;
    @Column(name="rider_id",nullable=false) private Long riderId;
    @Column(name="clock_out",nullable=false) private Instant clockOut;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private Status outcome;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="updated_by",nullable=false,length=254) private String updatedBy;
    protected PickupOutcome() { }
    PickupOutcome(Long org,Long rider,Instant time) { organizationId=org;riderId=rider;clockOut=time; }
    void update(Status status,String actor,Instant now) { outcome=status;updatedBy=actor;updatedAt=now; }
    public Long getRiderId() { return riderId; }
    public Instant getClockOut() { return clockOut; }
    public Status getOutcome() { return outcome; }
}
