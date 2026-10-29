package technology.renatus.routepulse.scheduling.automatic;

import jakarta.persistence.*;
import java.time.*;

/** Durable claim is committed before contacting SMTP. An uncertain send is never replayed. */
@Entity
@Table(name = "driving_shift_email_reminders")
public class DrivingReminderDelivery {
    public enum Status { SENDING, SENT, FAILED, UNCERTAIN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organization_id", nullable = false) private Long organizationId;
    @Column(name = "driver_id", nullable = false) private Long driverId;
    @Column(name = "work_starts_at", nullable = false) private Instant workStartsAt;
    @Column(nullable = false, length = 254) private String recipient;
    @Enumerated(EnumType.STRING) @Column(name = "delivery_status", nullable = false, length = 20) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(name = "last_attempt_at", nullable = false) private Instant lastAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;

    protected DrivingReminderDelivery() { }
    DrivingReminderDelivery(Long org, Long driver, Instant workStartsAt) {
        this.organizationId = org; this.driverId = driver; this.workStartsAt = workStartsAt;
    }
    boolean canAttempt(Instant now) {
        return status == null || status == Status.FAILED && attempts < 3
                && !now.isBefore(lastAttemptAt.plusSeconds(60));
    }
    void claim(String recipient, Instant now) {
        this.recipient = recipient; status = Status.SENDING; lastAttemptAt = now; attempts++;
    }
    void complete(Instant now) { status = Status.SENT; sentAt = now; }
    void fail(boolean definitelyNotSent) { status = definitelyNotSent ? Status.FAILED : Status.UNCERTAIN; }
    public Long getId() { return id; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
}
