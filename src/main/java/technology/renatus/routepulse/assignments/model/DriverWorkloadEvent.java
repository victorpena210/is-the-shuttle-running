package technology.renatus.routepulse.assignments.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import technology.renatus.routepulse.assignments.domain.DriverWorkloadEventType;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.organization.model.Organization;

@Entity
@Table(name = "driver_workload_events")
public class DriverWorkloadEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private DriverAssignment assignment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "session_id", updatable = false)
    private DrivingSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private DriverWorkloadEventType eventType;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recorded_by_user_id", updatable = false)
    private UserAccount recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DriverWorkloadEvent() {
    }

    public DriverWorkloadEvent(
            Driver driver,
            DriverAssignment assignment,
            DrivingSession session,
            DriverWorkloadEventType eventType,
            String reason,
            UserAccount recordedBy,
            Instant createdAt) {

        this.driver = Objects.requireNonNull(
                driver,
                "Driver is required."
        );
        this.assignment = Objects.requireNonNull(
                assignment,
                "Assignment is required."
        );
        this.organization = Objects.requireNonNull(
                assignment.getOrganization(),
                "Assignment organization is required."
        );

        if (!Objects.equals(
                this.organization.getId(),
                driver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "Driver and assignment must belong to the same organization."
            );
        }

        this.session = session;
        this.eventType = Objects.requireNonNull(
                eventType,
                "Workload event type is required."
        );
        this.reason = normalize(reason);
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }

    @PrePersist
    void ensureCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Driver getDriver() {
        return driver;
    }

    public DriverAssignment getAssignment() {
        return assignment;
    }

    public DrivingSession getSession() {
        return session;
    }

    public DriverWorkloadEventType getEventType() {
        return eventType;
    }

    public String getReason() {
        return reason;
    }

    public UserAccount getRecordedBy() {
        return recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
