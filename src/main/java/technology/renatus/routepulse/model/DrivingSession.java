package technology.renatus.routepulse.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

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

@Entity
@Table(name = "driving_sessions")
public class DrivingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false)
    private DriverAssignment assignment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private Driver driver;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "started_by_user_id", updatable = false)
    private UserAccount startedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ended_by_user_id")
    private UserAccount endedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_method", nullable = false, length = 20, updatable = false)
    private DrivingActionMethod startMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_method", length = 20)
    private DrivingActionMethod endMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DrivingSession() {
    }

    private DrivingSession(
            DriverAssignment assignment,
            Driver driver,
            LocalDateTime startedAt,
            UserAccount startedBy,
            DrivingActionMethod startMethod) {

        this.assignment = Objects.requireNonNull(
                assignment,
                "Driving assignment is required."
        );

        this.organization = Objects.requireNonNull(
                assignment.getOrganization(),
                "Driving assignment organization is required."
        );

        this.driver = Objects.requireNonNull(
                driver,
                "Actual driver is required."
        );

        if (!Objects.equals(
                this.organization.getId(),
                driver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The actual driver must belong to the assignment organization."
            );
        }

        this.startedAt = Objects.requireNonNull(
                startedAt,
                "Driving start time is required."
        );

        this.startedBy = Objects.requireNonNull(
                startedBy,
                "The account starting the driving session is required."
        );

        this.startMethod = Objects.requireNonNull(
                startMethod,
                "Driving start method is required."
        );
    }

    public static DrivingSession start(
            DriverAssignment assignment,
            Driver driver,
            LocalDateTime startedAt,
            UserAccount startedBy,
            DrivingActionMethod startMethod) {

        return new DrivingSession(
                assignment,
                driver,
                startedAt,
                startedBy,
                startMethod
        );
    }

    public void end(
            LocalDateTime endedAt,
            UserAccount endedBy,
            DrivingActionMethod endMethod) {

        Objects.requireNonNull(endedAt, "Driving end time is required.");

        if (this.endedAt != null) {
            throw new IllegalStateException(
                    "This driving session has already ended."
            );
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalStateException(
                    "Driving end time cannot be before the start time."
            );
        }

        this.endedAt = endedAt;
        this.endedBy = Objects.requireNonNull(
                endedBy,
                "The account ending the driving session is required."
        );
        this.endMethod = Objects.requireNonNull(
                endMethod,
                "Driving end method is required."
        );
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = startedAt != null
                    ? startedAt
                    : LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public DriverAssignment getAssignment() {
        return assignment;
    }

    public Driver getDriver() {
        return driver;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public UserAccount getStartedBy() {
        return startedBy;
    }

    public UserAccount getEndedBy() {
        return endedBy;
    }

    public DrivingActionMethod getStartMethod() {
        return startMethod;
    }

    public DrivingActionMethod getEndMethod() {
        return endMethod;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public boolean isCompleted() {
        return endedAt != null;
    }

    public long getActualMinutes() {
        if (!isCompleted()) {
            return 0;
        }

        return Math.max(
                0,
                Duration.between(startedAt, endedAt).toMinutes()
        );
    }

    public double getActualHours() {
        return getActualMinutes() / 60.0;
    }
}
