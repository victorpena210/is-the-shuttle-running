package technology.renatus.routepulse.driving.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;

/** A copy of the plan seen when driving began; never updated by calendar refreshes. */
@Embeddable
public class DrivingCoverageWindow {
    @Column(name="service_date",nullable=false,updatable=false) private LocalDate serviceDate;
    @Column(name="service_key",nullable=false,length=40,updatable=false) private String serviceKey;
    @Column(name="scheduled_driver_id",nullable=false,updatable=false) private Long scheduledDriverId;
    @Column(name="starts_at",nullable=false,updatable=false) private Instant start;
    @Column(name="ends_at",nullable=false,updatable=false) private Instant end;

    protected DrivingCoverageWindow() {}
    public DrivingCoverageWindow(LocalDate serviceDate,String serviceKey,Long scheduledDriverId,Instant start,Instant end) {
        this.serviceDate=Objects.requireNonNull(serviceDate);
        this.serviceKey=Objects.requireNonNull(serviceKey);
        this.scheduledDriverId=Objects.requireNonNull(scheduledDriverId);
        this.start=Objects.requireNonNull(start);this.end=Objects.requireNonNull(end);
        if(!end.isAfter(start)) throw new IllegalArgumentException("Coverage window must end after it starts.");
    }
    public LocalDate getServiceDate() {return serviceDate;}
    public String getServiceKey() {return serviceKey;}
    public Long getScheduledDriverId() {return scheduledDriverId;}
    public Instant getStart() {return start;}
    public Instant getEnd() {return end;}
}
