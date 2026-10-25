package technology.renatus.routepulse.pickup;

import jakarta.persistence.*;
import java.time.Instant;
import technology.renatus.routepulse.driver.calendar.UkgCalendarParser;

@Entity @Table(name="pickup_shifts")
public class PickupShift {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="rider_id",nullable=false) private PickupRider rider;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(nullable=false,length=200) private String location;
    protected PickupShift() { }
    public PickupShift(PickupRider rider,UkgCalendarParser.DetailedShift shift) {
        this.rider=rider;startsAt=shift.start();endsAt=shift.end();location=shift.location();
    }
    public PickupRider getRider(){return rider;} public Instant getStartsAt(){return startsAt;}
    public Instant getEndsAt(){return endsAt;} public String getLocation(){return location;}
}
