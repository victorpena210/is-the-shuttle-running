package technology.renatus.routepulse.assignments.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import technology.renatus.routepulse.assignments.domain.AssignmentOutcome;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

@Entity
@Table(
        name = "driver_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_driver_assignments_org_date_period",
                        columnNames = {
                                "organization_id",
                                "assignment_date",
                                "shuttle_period"
                        }
                )
        }
)
public class DriverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            updatable = false
    )
    private Organization organization;

    @Column(name = "assignment_date", nullable = false)
    private LocalDate assignmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shuttle_period", nullable = false, length = 20)
    private ShuttlePeriod period;

    /*
     * IMPORTANT:
     * scheduled_driver_id is the permanent planned/scheduled driver.
     * Driver checkpoint actions never rewrite this field. Actual drivers live in
     * driving_sessions.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "scheduled_driver_id", nullable = false)
    private Driver scheduledDriver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "originally_suggested_driver_id")
    private Driver originallySuggestedDriver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_schedule_import_id", updatable = false)
    private ScheduleImport sourceScheduleImport;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_schedule_import_id")
    private ScheduleImport reviewedScheduleImport;

    @Column(name = "schedule_reviewed_at")
    private Instant scheduleReviewedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_reviewed_by_user_id")
    private UserAccount scheduleReviewedBy;

    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    /**
     * Detailed planned driving intervals inside the broad scheduled window.
     *
     * A morning/afternoon break is represented by the gap between two
     * intervals. Midday can contain several intervals because turnover demand
     * is intentionally intermittent. Legacy/manual assignments may leave this
     * collection empty and continue using scheduledStartTime/scheduledEndTime.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "assignment_planned_driving_windows",
            joinColumns = @JoinColumn(name = "driver_assignment_id", nullable = false)
    )
    @OrderColumn(name = "window_order", nullable = false)
    private List<PlannedDrivingWindow> plannedDrivingWindows = new java.util.ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, length = 20)
    private AssignmentSource assignmentSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_outcome", nullable = false, length = 40)
    private AssignmentOutcome assignmentOutcome = AssignmentOutcome.PENDING;

    @Column(name = "outcome_reason", length = 500)
    private String outcomeReason;

    @Column(name = "outcome_reviewed_at")
    private Instant outcomeReviewedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "outcome_reviewed_by_user_id")
    private UserAccount outcomeReviewedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_user_id")
    private UserAccount changedBy;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /*
     * The sessions are related history, not columns on the assignment.
     * EAGER is intentional here because the existing dashboard and records
     * pages render assignment status after the service transaction closes.
     */
    @OneToMany(mappedBy = "assignment", fetch = FetchType.EAGER)
    @OrderBy("startedAt ASC")
    private List<DrivingSession> drivingSessions = new java.util.ArrayList<>();

    protected DriverAssignment() {
    }

    private DriverAssignment(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver scheduledDriver,
            Driver originallySuggestedDriver,
            ScheduleImport sourceScheduleImport,
            AssignmentSource assignmentSource,
            String changeReason) {

        this.scheduledDriver = Objects.requireNonNull(
                scheduledDriver,
                "Driver is required."
        );

        this.organization = Objects.requireNonNull(
                scheduledDriver.getOrganization(),
                "Driver organization is required."
        );

        this.assignmentDate = Objects.requireNonNull(
                assignmentDate,
                "Assignment date is required."
        );

        this.period = Objects.requireNonNull(
                period,
                "Shuttle period is required."
        );

        this.originallySuggestedDriver = originallySuggestedDriver;
        this.sourceScheduleImport = sourceScheduleImport;
        this.assignmentSource = Objects.requireNonNull(
                assignmentSource,
                "Assignment source is required."
        );
        this.assignmentOutcome = AssignmentOutcome.PENDING;
        this.changeReason = normalizeReason(changeReason);
    }

    public static DriverAssignment automatic(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver suggestedDriver) {

        return automatic(
                assignmentDate,
                period,
                suggestedDriver,
                null
        );
    }

    public static DriverAssignment automatic(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver suggestedDriver,
            ScheduleImport sourceScheduleImport) {

        return new DriverAssignment(
                assignmentDate,
                period,
                suggestedDriver,
                suggestedDriver,
                sourceScheduleImport,
                AssignmentSource.AUTO,
                null
        );
    }

    public static DriverAssignment manual(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver driver,
            String reason) {

        return new DriverAssignment(
                assignmentDate,
                period,
                driver,
                null,
                null,
                AssignmentSource.MANUAL,
                reason
        );
    }

    public void updateScheduledTimes(
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime) {

        ensureDrivingHasNotStarted(
                "Scheduled times cannot be changed after driving has started."
        );

        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;

        // A manual broad-window edit invalidates any previously generated
        // detailed windows. Automatic planning repopulates them immediately.
        if (plannedDrivingWindows != null) {
            plannedDrivingWindows.clear();
        }
    }

    public void replacePlannedDrivingWindows(
            List<PlannedDrivingWindow> windows) {

        ensureDrivingHasNotStarted(
                "Planned driving windows cannot be changed after driving has started."
        );

        if (plannedDrivingWindows == null) {
            plannedDrivingWindows = new java.util.ArrayList<>();
        } else {
            plannedDrivingWindows.clear();
        }

        if (windows == null || windows.isEmpty()) {
            return;
        }

        LocalTime previousEnd = null;
        for (PlannedDrivingWindow window : windows.stream()
                .sorted(java.util.Comparator.comparing(PlannedDrivingWindow::getStartTime))
                .toList()) {

            Objects.requireNonNull(window, "Planned driving window is required.");

            if (scheduledStartTime != null
                    && window.getStartTime().isBefore(scheduledStartTime)) {
                throw new IllegalArgumentException(
                        "A planned driving window cannot begin before the assignment start."
                );
            }

            if (scheduledEndTime != null
                    && window.getEndTime().isAfter(scheduledEndTime)) {
                throw new IllegalArgumentException(
                        "A planned driving window cannot end after the assignment end."
                );
            }

            if (previousEnd != null && window.getStartTime().isBefore(previousEnd)) {
                throw new IllegalArgumentException(
                        "Planned driving windows cannot overlap."
                );
            }

            plannedDrivingWindows.add(window);
            previousEnd = window.getEndTime();
        }
    }

    public boolean changeDriver(
            Driver newDriver,
            String reason,
            UserAccount actor) {

        ensureDrivingHasNotStarted(
                "The assigned driver cannot be changed after driving has started."
        );

        Objects.requireNonNull(newDriver, "New driver is required.");

        if (!Objects.equals(
                organization.getId(),
                newDriver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The replacement driver must belong to the same organization."
            );
        }

        if (Objects.equals(
                scheduledDriver.getId(),
                newDriver.getId()
        )) {
            return false;
        }

        this.scheduledDriver = newDriver;

        if (assignmentSource == AssignmentSource.AUTO) {
            assignmentSource = AssignmentSource.OVERRIDE;
        }

        changedAt = Instant.now();
        changedBy = Objects.requireNonNull(
                actor,
                "The manager making the change is required."
        );
        changeReason = normalizeReason(reason);

        return true;
    }

    public void confirmScheduleReview(
            ScheduleImport scheduleImport,
            UserAccount actor,
            Instant reviewedAt) {

        ensureDrivingHasNotStarted(
                "Schedule coverage cannot be confirmed after driving has started."
        );

        Objects.requireNonNull(scheduleImport, "Schedule import is required.");

        if (!Objects.equals(
                organization.getId(),
                scheduleImport.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The schedule import must belong to the assignment organization."
            );
        }

        reviewedScheduleImport = scheduleImport;
        scheduleReviewedBy = Objects.requireNonNull(
                actor,
                "The manager reviewing coverage is required."
        );
        scheduleReviewedAt = Objects.requireNonNull(
                reviewedAt,
                "Schedule review time is required."
        );
    }

    public boolean requiresReviewAgainst(Long latestScheduleImportId) {
        if (assignmentSource == AssignmentSource.MANUAL) {
            return false;
        }

        if (latestScheduleImportId == null) {
            return true;
        }

        ScheduleImport comparisonImport = reviewedScheduleImport == null
                ? sourceScheduleImport
                : reviewedScheduleImport;

        if (comparisonImport == null) {
            return true;
        }

        return !Objects.equals(
                comparisonImport.getId(),
                latestScheduleImportId
        );
    }

    public void markDrivingInProgress() {
        if (outcomeReviewedAt == null) {
            assignmentOutcome = AssignmentOutcome.IN_PROGRESS;
        }
    }

    public void updateDerivedOutcome(AssignmentOutcome outcome) {
        Objects.requireNonNull(outcome, "Assignment outcome is required.");

        if (outcomeReviewedAt == null) {
            assignmentOutcome = outcome;
        }
    }

    public void reviewOutcome(
            AssignmentOutcome outcome,
            String reason,
            UserAccount actor,
            Instant reviewedAt) {

        Objects.requireNonNull(outcome, "Assignment outcome is required.");

        if (outcome == AssignmentOutcome.PENDING
                || outcome == AssignmentOutcome.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "Choose a completed, missed, excused, or cancelled outcome."
            );
        }

        assignmentOutcome = outcome;
        outcomeReason = normalizeReason(reason);
        outcomeReviewedBy = Objects.requireNonNull(
                actor,
                "The manager reviewing the outcome is required."
        );
        outcomeReviewedAt = Objects.requireNonNull(
                reviewedAt,
                "Outcome review time is required."
        );
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (assignmentOutcome == null) {
            assignmentOutcome = AssignmentOutcome.PENDING;
        }
    }

    private void ensureDrivingHasNotStarted(String message) {
        if (hasDrivingSessions()) {
            throw new IllegalStateException(message);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        String cleaned = reason.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public LocalDate getAssignmentDate() {
        return assignmentDate;
    }

    public ShuttlePeriod getPeriod() {
        return period;
    }

    public Driver getScheduledDriver() {
        return scheduledDriver;
    }

    /*
     * Backward-compatible alias used by existing templates/tests.
     * From V26 forward this always means SCHEDULED driver.
     */
    public Driver getDriver() {
        return scheduledDriver;
    }

    /*
     * Presentation compatibility for existing pages. Before driving starts,
     * this is the scheduled driver. Once a session exists, it is the most
     * recent actual driver. The persisted scheduled driver never changes.
     */
    public Driver getFinalDriver() {
        return drivingHistory().finalDriver();
    }

    public Driver getOriginallySuggestedDriver() {
        return originallySuggestedDriver;
    }

    public ScheduleImport getSourceScheduleImport() {
        return sourceScheduleImport;
    }

    public ScheduleImport getReviewedScheduleImport() {
        return reviewedScheduleImport;
    }

    public Instant getScheduleReviewedAt() {
        return scheduleReviewedAt;
    }

    public UserAccount getScheduleReviewedBy() {
        return scheduleReviewedBy;
    }

    public LocalTime getScheduledStartTime() {
        return scheduledStartTime;
    }

    public LocalTime getScheduledEndTime() {
        return scheduledEndTime;
    }

    public List<PlannedDrivingWindow> getPlannedDrivingWindows() {
        return plannedDrivingWindows == null
                ? List.of()
                : List.copyOf(plannedDrivingWindows);
    }

    public boolean hasPlannedDrivingWindows() {
        return plannedDrivingWindows != null && !plannedDrivingWindows.isEmpty();
    }

    public boolean hasMultiplePlannedDrivingWindows() {
        return plannedDrivingWindows != null && plannedDrivingWindows.size() > 1;
    }

    public boolean isInsidePlannedDrivingWindow(LocalTime time) {
        return getPlannedDrivingWindows().stream().anyMatch(window -> window.contains(time));
    }

    public LocalTime getNextPlannedDrivingStart(LocalTime time) {
        if (time == null) {
            return null;
        }

        return getPlannedDrivingWindows().stream()
                .filter(window -> !window.getEndTime().isBefore(time))
                .filter(window -> time.isBefore(window.getStartTime()) || window.contains(time))
                .map(window -> window.contains(time) ? time : window.getStartTime())
                .findFirst()
                .orElse(null);
    }

    public boolean hasRemainingPlannedDrivingWindowAtOrAfter(LocalTime time) {
        if (time == null) {
            return false;
        }
        return getPlannedDrivingWindows().stream()
                .anyMatch(window -> window.contains(time) || window.getStartTime().isAfter(time));
    }

    /**
     * Gaps between generated driving windows. Morning/afternoon render these
     * as protected breaks; midday treats them simply as off-driving time.
     */
    public List<PlannedDrivingWindow> getPlannedBreakWindows() {
        List<PlannedDrivingWindow> windows = getPlannedDrivingWindows();
        if (windows.size() < 2) {
            return List.of();
        }

        List<PlannedDrivingWindow> gaps = new java.util.ArrayList<>();
        for (int index = 0; index < windows.size() - 1; index++) {
            LocalTime gapStart = windows.get(index).getEndTime();
            LocalTime gapEnd = windows.get(index + 1).getStartTime();
            if (gapEnd.isAfter(gapStart)) {
                gaps.add(new PlannedDrivingWindow(gapStart, gapEnd));
            }
        }
        return List.copyOf(gaps);
    }

    public long getScheduledMinutes() {
        if (hasPlannedDrivingWindows()) {
            return getPlannedDrivingWindows().stream()
                    .mapToLong(PlannedDrivingWindow::getMinutes)
                    .sum();
        }

        if (scheduledStartTime == null || scheduledEndTime == null) {
            return 0;
        }

        return Duration.between(
                scheduledStartTime,
                scheduledEndTime
        ).toMinutes();
    }

    public double getScheduledHours() {
        return getScheduledMinutes() / 60.0;
    }

    public AssignmentSource getAssignmentSource() {
        return assignmentSource;
    }

    public AssignmentOutcome getAssignmentOutcome() {
        return assignmentOutcome;
    }

    public String getOutcomeReason() {
        return outcomeReason;
    }

    public Instant getOutcomeReviewedAt() {
        return outcomeReviewedAt;
    }

    public UserAccount getOutcomeReviewedBy() {
        return outcomeReviewedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public UserAccount getChangedBy() {
        return changedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void registerDrivingSession(DrivingSession session) {
        Objects.requireNonNull(session, "Driving session is required.");

        if (drivingSessions == null) {
            drivingSessions = new java.util.ArrayList<>();
        }

        if (!drivingSessions.contains(session)) {
            drivingSessions.add(session);
        }
    }

    public List<DrivingSession> getDrivingSessions() {
        return drivingSessions == null ? List.of() : List.copyOf(drivingSessions);
    }

    public boolean hasDrivingSessions() {
        return drivingHistory().hasSessions();
    }

    public DrivingSession getActiveDrivingSession() {
        return drivingHistory().activeSession();
    }

    public Instant getActualStartedAt() {
        return drivingHistory().actualStartedAt();
    }

    public UserAccount getActualStartedBy() {
        return drivingHistory().actualStartedBy();
    }

    public Instant getActualEndedAt() {
        return drivingHistory().actualEndedAt();
    }

    public UserAccount getActualEndedBy() {
        return drivingHistory().actualEndedBy();
    }

    public boolean isDriving() {
        return drivingHistory().isDriving();
    }

    public boolean isDrivingCompleted() {
        return drivingHistory().isCompleted();
    }

    public boolean isDrivingPaused() {
        return drivingHistory().isPaused();
    }

    public long getActualMinutes() {
        return drivingHistory().actualMinutes();
    }

    public double getActualHours() {
        return getActualMinutes() / 60.0;
    }

    public Long getActualVsScheduledMinutes() {
        if (!isDrivingCompleted()
                || scheduledStartTime == null
                || scheduledEndTime == null) {
            return null;
        }
        return getActualMinutes() - getScheduledMinutes();
    }

    public String getActualDriverNames() {
        return drivingHistory().actualDriverNames();
    }

    private DriverAssignmentDrivingHistory drivingHistory() {
        return new DriverAssignmentDrivingHistory(drivingSessions, scheduledDriver);
    }

}
