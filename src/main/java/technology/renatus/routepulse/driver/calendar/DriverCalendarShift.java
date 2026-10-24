package technology.renatus.routepulse.driver.calendar;

import jakarta.persistence.*;
import java.time.*;

@Entity @Table(name="driver_calendar_shifts")
public class DriverCalendarShift {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="connection_id",nullable=false) private DriverCalendarConnection connection;
    @Column(name="starts_at",nullable=false) private Instant startsAt;
    @Column(name="ends_at",nullable=false) private Instant endsAt;
    protected DriverCalendarShift() { }
    public DriverCalendarShift(DriverCalendarConnection connection, UkgCalendarParser.Shift shift) {
        this.connection=connection; startsAt=shift.start(); endsAt=shift.end();
    }
    public DriverCalendarConnection getConnection() { return connection; }
    public Instant getStartsAt() { return startsAt; } public Instant getEndsAt() { return endsAt; }
}
