package technology.renatus.routepulse.driving.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;

/** A copy of the plan seen when driving began, with separately recorded verified corrections. */
@Embeddable
public class DrivingCoverageWindow {
    @Column(name="service_date",nullable=false,updatable=false) private LocalDate serviceDate;
    @Column(name="service_key",nullable=false,length=40,updatable=false) private String serviceKey;
    @Column(name="scheduled_driver_id",nullable=false,updatable=false) private Long scheduledDriverId;
    @Column(name="corrected_driver_id",updatable=false) private Long correctedDriverId;
    @Column(name="assignment_correction_reason",length=1000,updatable=false) private String assignmentCorrectionReason;
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
    /** Calendar refreshes never rewrite history; only an explicit verified correction overrides it. */
    public Long getEffectiveScheduledDriverId() {return correctedDriverId==null?scheduledDriverId:correctedDriverId;}
    public String getAssignmentCorrectionReason() {return assignmentCorrectionReason;}
    public Instant getStart() {return start;}
    public Instant getEnd() {return end;}
}
