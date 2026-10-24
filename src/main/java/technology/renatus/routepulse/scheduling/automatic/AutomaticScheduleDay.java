package technology.renatus.routepulse.scheduling.automatic;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import technology.renatus.routepulse.scheduling.demand.ShuttleService;

/** Planned work, separate from actual driving sessions and public status. */
@Entity
@Table(name="automatic_schedule_days", uniqueConstraints=@UniqueConstraint(columnNames={"organization_id","service_date"}))
public class AutomaticScheduleDay {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="organization_id",nullable=false) private Long organizationId;
    @Column(name="service_date",nullable=false) private LocalDate date;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false,length=80) private String source;
    @Column(name="park_opening") private LocalTime parkOpening;
    @Column(name="park_closing") private LocalTime parkClosing;
    @Column(name="wardrobe_opening") private LocalTime wardrobeOpening;
    @Column(name="wardrobe_closing") private LocalTime wardrobeClosing;
    @Column(name="refreshed_at",nullable=false) private Instant refreshedAt;
    @Column(nullable=false,length=500) private String message;
    @ElementCollection
    @CollectionTable(name="automatic_driving_slots",joinColumns=@JoinColumn(name="schedule_day_id"))
    @OrderColumn(name="slot_order")
    private List<Slot> slots = new ArrayList<>();
    protected AutomaticScheduleDay() {}
    AutomaticScheduleDay(Long org,LocalDate date) { this.organizationId=org; this.date=date; }
    void update(String status,String source,LocalTime parkOpening,LocalTime parkClosing,
            LocalTime wardrobeOpening,LocalTime wardrobeClosing,Instant now,String message,List<Slot> next) {
        this.status=status; this.source=source; this.parkOpening=parkOpening; this.parkClosing=parkClosing;
        this.wardrobeOpening=wardrobeOpening; this.wardrobeClosing=wardrobeClosing;
        this.refreshedAt=now; this.message=message;
        if (!slots.equals(next)) { slots.clear(); slots.addAll(next); }
    }
    List<Slot> slots() { return List.copyOf(slots); }
    View view(ZoneId zone,Long driver) {
        return new View(date,status,source,parkOpening,parkClosing,wardrobeOpening,wardrobeClosing,
                refreshedAt.atZone(zone),message,slots.stream().filter(s -> driver==null || driver.equals(s.driverId)).toList());
    }
    public record View(LocalDate date,String status,String source,LocalTime parkOpening,LocalTime parkClosing,
            LocalTime wardrobeOpening,LocalTime wardrobeClosing,ZonedDateTime refreshedAt,String message,List<Slot> slots,
            List<AutomaticScheduleHistory.RecordedRun> recordedRuns,boolean savedPlan) {
        public View(LocalDate date,String status,String source,LocalTime parkOpening,LocalTime parkClosing,
                LocalTime wardrobeOpening,LocalTime wardrobeClosing,ZonedDateTime refreshedAt,String message,List<Slot> slots) {
            this(date,status,source,parkOpening,parkClosing,wardrobeOpening,wardrobeClosing,refreshedAt,message,slots,List.of(),true);
        }
        public View withHistory(List<AutomaticScheduleHistory.RecordedRun> runs) {
            return new View(date,status,source,parkOpening,parkClosing,wardrobeOpening,wardrobeClosing,
                    refreshedAt,message,slots,List.copyOf(runs),savedPlan);
        }
        public LocalDateTime openingStart() {
            return "OPEN".equals(status) && wardrobeOpening!=null ? date.atTime(wardrobeOpening) : null;
        }
        public LocalDateTime openingEnd() {
            if(openingStart()==null || wardrobeClosing==null) return null;
            var end=openingStart().plusHours(wardrobeOpening.isBefore(LocalTime.NOON)?3:2);
            var closing=wardrobeClosing.equals(LocalTime.MIDNIGHT)?date.plusDays(1).atStartOfDay():date.atTime(wardrobeClosing);
            return end.isBefore(closing)?end:closing;
        }
        public boolean openingPlanMissing() {
            return openingEnd()!=null && slots.stream().noneMatch(s->s.overlaps(openingStart(),openingEnd()));
        }
    }

    @Embeddable
    public static class Slot {
        @Column(name="starts_at",nullable=false) private LocalDateTime start;
        @Column(name="ends_at",nullable=false) private LocalDateTime end;
        @Column(name="driver_id") private Long driverId;
        @Column(name="driver_name",length=255) private String driverName;
        @Column(nullable=false,length=255) private String reason;
        @Enumerated(EnumType.STRING) @Column(name="service_kind",length=32) private ShuttleService service;
        protected Slot() {}
        public Slot(LocalDateTime start,LocalDateTime end,Long driverId,String driverName,String reason) {
            this(start,end,driverId,driverName,reason,ShuttleService.EMPLOYEE_PICKUP);
        }
        public Slot(LocalDateTime start,LocalDateTime end,Long driverId,String driverName,String reason,ShuttleService service) {
            if (!end.isAfter(start)) throw new IllegalArgumentException("A shift must end after it starts.");
            this.start=start; this.end=end; this.driverId=driverId; this.driverName=driverName; this.reason=reason;
            this.service=service;
        }
        public LocalDateTime getStart() {return start;}
        public LocalDateTime getEnd() {return end;}
        public Long getDriverId() {return driverId;}
        public String getDriverName() {return driverName;}
        public String getReason() {return reason;}
        public ShuttleService getService() {return service==null?ShuttleService.EMPLOYEE_PICKUP:service;}
        public String getServiceLabel() {return getService().getLabel();}
        public boolean isScareActorTransport() {return getService()==ShuttleService.SCARE_ACTORS;}
        public long seconds() {return Duration.between(start,end).getSeconds();}
        public boolean overlaps(LocalDateTime from,LocalDateTime to) {return start.isBefore(to)&&end.isAfter(from);}
        @Override public boolean equals(Object value) {
            return value instanceof Slot s && Objects.equals(start,s.start)&&Objects.equals(end,s.end)
                    &&Objects.equals(driverId,s.driverId)&&Objects.equals(driverName,s.driverName)&&Objects.equals(reason,s.reason)
                    &&getService()==s.getService();
        }
        @Override public int hashCode() {return Objects.hash(start,end,driverId,driverName,reason,getService());}
    }
}
