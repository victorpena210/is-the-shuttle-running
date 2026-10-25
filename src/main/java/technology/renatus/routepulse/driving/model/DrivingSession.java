package technology.renatus.routepulse.driving.model;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.domain.DrivingSessionEndReason;
import technology.renatus.routepulse.organization.model.Organization;

@Entity
@Table(name = "driving_sessions")
public class DrivingSession {

    public static final Duration MAX_DURATION = Duration.ofHours(4);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", updatable = false)
    private DriverAssignment assignment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private Driver driver;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_user_id")
    private UserAccount confirmedBy;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private DrivingSessionEndReason endReason;

    @Column(name = "duration_review_required", nullable = false)
    private boolean durationReviewRequired;

    @Column(name = "time_reviewed_at")
    private Instant timeReviewedAt;

    @Column(name = "time_review_revision", nullable = false)
    private long timeReviewRevision;

    @Column(name = "coverage_plan_captured", nullable = false, updatable = false)
    private boolean coveragePlanCaptured;

    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "driving_coverage_windows", joinColumns = @JoinColumn(name = "session_id"))
    @jakarta.persistence.OrderColumn(name = "window_order")
    private java.util.List<DrivingCoverageWindow> coveragePlan = new java.util.ArrayList<>();

    /** Capture once, including an empty plan. Later refreshes and time reviews cannot rewrite it. */
    public void captureCoveragePlan(java.util.List<DrivingCoverageWindow> windows) {
        if (coveragePlanCaptured) throw new IllegalStateException("The original coverage plan has already been saved.");
        coveragePlan.addAll(java.util.List.copyOf(windows));
        coveragePlanCaptured = true;
    }
    public boolean isCoveragePlanCaptured() { return coveragePlanCaptured; }
    public java.util.List<DrivingCoverageWindow> getCoveragePlan() { return java.util.List.copyOf(coveragePlan); }

    public boolean isDurationReviewNeeded() { return durationReviewRequired && timeReviewedAt == null; }
    public Instant getTimeReviewedAt() { return timeReviewedAt; }
    public long getTimeReviewRevision() { return timeReviewRevision; }

    /** Only the manager review service may call this, after locking and recording an audit entry. */
    public void reviewTimes(Instant start, Instant end, UserAccount actor, Instant now) {
        Objects.requireNonNull(actor, "A reviewing manager is required.");
        if (start == null || end == null || !end.isAfter(start) || end.isAfter(now))
            throw new IllegalArgumentException("The end must be after the start and cannot be in the future.");
        boolean wasActive = isActive();
        durationReviewRequired = durationReviewRequired || hasReachedDurationLimit(now)
                || Duration.between(start, end).compareTo(MAX_DURATION) >= 0;
        if (wasActive) {
            endMethod = DrivingActionMethod.MANAGER;
            endReason = DrivingSessionEndReason.COMPLETED;
            endedBy = actor;
            getMovement().clearCoordinates();
        }
        startedAt = start;
        endedAt = end;
        timeReviewedAt = now;
        timeReviewRevision++;
    }

    @jakarta.persistence.Embedded
    private technology.renatus.routepulse.driving.movement.MovementState movement = new technology.renatus.routepulse.driving.movement.MovementState();

    @Column(name = "inactivity_reviewed_at") private Instant inactivityReviewedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inactivity_reviewed_by") private UserAccount inactivityReviewedBy;
    @Column(name = "inactivity_review_note", length = 1000) private String inactivityReviewNote;

    public technology.renatus.routepulse.driving.movement.MovementState getMovement() {
        if (movement == null) movement = new technology.renatus.routepulse.driving.movement.MovementState();
        return movement;
    }
    public void confirmMovement(Instant now) {
        if (!isActive()) throw new IllegalStateException("This session has ended.");
        if (!now.isBefore(getLastConfirmedAt())) { lastConfirmedAt = now; confirmedBy = null; }
    }
    public boolean isInactivityReviewNeeded() {
        return endReason == DrivingSessionEndReason.INACTIVITY_TIMEOUT && inactivityReviewedAt == null;
    }
    public Instant getInactivityReviewedAt() { return inactivityReviewedAt; }
    public String getInactivityReviewNote() { return inactivityReviewNote; }
    public void reviewInactivity(UserAccount actor, String note, Instant now) {
        if (!isInactivityReviewNeeded()) throw new IllegalStateException("This session does not need an inactivity review.");
        if (note == null || note.isBlank() || note.trim().length() > 1000)
            throw new IllegalArgumentException("Add a review note of 1–1000 characters.");
        inactivityReviewedBy = Objects.requireNonNull(actor); inactivityReviewedAt = now; inactivityReviewNote = note.trim();
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DrivingSession() {
    }

    private DrivingSession(
            Organization organization,
            DriverAssignment assignment,
            Driver driver,
            Instant startedAt,
            UserAccount startedBy,
            DrivingActionMethod startMethod) {

        this.assignment = assignment;
        this.organization = Objects.requireNonNull(organization, "Organization is required.");

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
            Instant startedAt,
            UserAccount startedBy,
            DrivingActionMethod startMethod) {

        return new DrivingSession(
                Objects.requireNonNull(assignment, "Assignment is required.").getOrganization(),
                assignment,
                driver,
                startedAt,
                startedBy,
                startMethod
        );
    }

    public static DrivingSession startUnscheduled(
            Organization organization, Driver driver, Instant startedAt,
            UserAccount startedBy, DrivingActionMethod startMethod) {
        return new DrivingSession(organization, null, driver, startedAt, startedBy, startMethod);
    }

    public boolean isUnscheduled() {
        return assignment == null;
    }

    public void end(
            Instant endedAt,
            UserAccount endedBy,
            DrivingActionMethod endMethod) {

        end(
                endedAt,
                endedBy,
                endMethod,
                DrivingSessionEndReason.COMPLETED
        );
    }

    public void end(
            Instant endedAt,
            UserAccount endedBy,
            DrivingActionMethod endMethod,
            DrivingSessionEndReason endReason) {

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

        // A late stop or handoff must not extend a run past the server cutoff,
        // even if it arrives between background checks.
        if (!endedAt.isBefore(getAutomaticEndAt())) {
            endedAt = getAutomaticEndAt();
            endMethod = DrivingActionMethod.AUTOMATIC;
            endReason = DrivingSessionEndReason.DURATION_LIMIT;
        }
        boolean automated = endMethod == DrivingActionMethod.AUTOMATIC
                && (endReason == DrivingSessionEndReason.INACTIVITY_TIMEOUT
                    || endReason == DrivingSessionEndReason.DURATION_LIMIT);
        if (!automated) Objects.requireNonNull(endedBy, "The account ending the driving session is required.");
        this.endedAt = endedAt;
        this.endedBy = automated ? null : endedBy;
        getMovement().clearCoordinates();
        this.endMethod = Objects.requireNonNull(
                endMethod,
                "Driving end method is required."
        );
        this.endReason = Objects.requireNonNull(
                endReason,
                "Driving end reason is required."
        );
        if (endReason == DrivingSessionEndReason.DURATION_LIMIT) durationReviewRequired = true;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = startedAt != null
                    ? startedAt
                    : Instant.now();
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getAutomaticEndAt() {
        return startedAt.plus(MAX_DURATION);
    }

    public boolean hasReachedDurationLimit(Instant now) {
        return isActive() && !now.isBefore(getAutomaticEndAt());
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Instant getLastConfirmedAt() { return lastConfirmedAt == null ? startedAt : lastConfirmedAt; }

    public void confirmRunning(Instant now, UserAccount actor) {
        Objects.requireNonNull(now, "Confirmation time is required.");
        Objects.requireNonNull(actor, "Confirming account is required.");
        if (!isActive() || now.isBefore(getLastConfirmedAt()))
            throw new IllegalStateException("Only an active session can be confirmed at the current time.");
        lastConfirmedAt = now;
        confirmedBy = actor;
        getMovement().acknowledge(now, false);
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

    public DrivingSessionEndReason getEndReason() {
        return endReason;
    }

    public Instant getCreatedAt() {
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
