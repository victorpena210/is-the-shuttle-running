package technology.renatus.routepulse.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "driver_assignments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name =
                                "uk_driver_assignments_org_date_period",
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
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            updatable = false
    )
    private Organization organization;

    @Column(
            name = "assignment_date",
            nullable = false
    )
    private LocalDate assignmentDate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "shuttle_period",
            nullable = false,
            length = 20
    )
    private ShuttlePeriod period;

    /*
     * driver_id is the final/current driver for the assignment.
     * If a manager overrides an automatic recommendation,
     * this field changes while originallySuggestedDriver stays put.
     */
    @ManyToOne(
            fetch = FetchType.EAGER,
            optional = false
    )
    @JoinColumn(
            name = "driver_id",
            nullable = false
    )
    private Driver driver;

    @ManyToOne(
            fetch = FetchType.EAGER
    )
    @JoinColumn(
            name = "originally_suggested_driver_id"
    )
    private Driver originallySuggestedDriver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "source_schedule_import_id",
            updatable = false
    )
    private ScheduleImport sourceScheduleImport;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_schedule_import_id")
    private ScheduleImport reviewedScheduleImport;

    @Column(name = "schedule_reviewed_at")
    private LocalDateTime scheduleReviewedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_reviewed_by_user_id")
    private UserAccount scheduleReviewedBy;

    @Column(
            name = "scheduled_start_time"
    )
    private LocalTime scheduledStartTime;

    @Column(
            name = "scheduled_end_time"
    )
    private LocalTime scheduledEndTime;

    @Column(
            name = "actual_started_at"
    )
    private LocalDateTime actualStartedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actual_started_by_user_id")
    private UserAccount actualStartedBy;

    @Column(
            name = "actual_ended_at"
    )
    private LocalDateTime actualEndedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actual_ended_by_user_id")
    private UserAccount actualEndedBy;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "assignment_source",
            nullable = false,
            length = 20
    )
    private AssignmentSource assignmentSource;

    @Column(
            name = "changed_at"
    )
    private LocalDateTime changedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_user_id")
    private UserAccount changedBy;

    @Column(
            name = "change_reason",
            length = 500
    )
    private String changeReason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected DriverAssignment() {
    }

    private DriverAssignment(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver driver,
            Driver originallySuggestedDriver,
            ScheduleImport sourceScheduleImport,
            AssignmentSource assignmentSource,
            String changeReason) {

        this.driver =
                Objects.requireNonNull(
                        driver,
                        "Driver is required."
                );

        this.organization =
                Objects.requireNonNull(
                        driver.getOrganization(),
                        "Driver organization is required."
                );

        this.assignmentDate =
                Objects.requireNonNull(
                        assignmentDate,
                        "Assignment date is required."
                );

        this.period =
                Objects.requireNonNull(
                        period,
                        "Shuttle period is required."
                );

        this.originallySuggestedDriver =
                originallySuggestedDriver;

        this.sourceScheduleImport =
                sourceScheduleImport;

        this.assignmentSource =
                Objects.requireNonNull(
                        assignmentSource,
                        "Assignment source is required."
                );

        this.changeReason =
                normalizeReason(changeReason);
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

        this.scheduledStartTime =
                scheduledStartTime;

        this.scheduledEndTime =
                scheduledEndTime;
    }

    public boolean changeDriver(
            Driver newDriver,
            String reason,
            UserAccount actor) {

        ensureDrivingHasNotStarted(
                "The assigned driver cannot be changed after driving has started."
        );

        Objects.requireNonNull(
                newDriver,
                "New driver is required."
        );

        if (!Objects.equals(
                organization.getId(),
                newDriver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The replacement driver must belong "
                            + "to the same organization."
            );
        }

        if (Objects.equals(
                driver.getId(),
                newDriver.getId()
        )) {
            return false;
        }

        this.driver = newDriver;

        /*
         * AUTO becomes OVERRIDE the first time a manager
         * changes the algorithm's recommendation.
         * OVERRIDE remains OVERRIDE forever after that.
         * MANUAL remains MANUAL because no algorithmic
         * recommendation existed for that assignment.
         */
        if (assignmentSource == AssignmentSource.AUTO) {
            assignmentSource = AssignmentSource.OVERRIDE;
        }

        changedAt = LocalDateTime.now();
        changedBy = Objects.requireNonNull(
                actor,
                "The manager making the change is required."
        );
        changeReason = normalizeReason(reason);

        return true;
    }

    public boolean overrideDriverFromQr(
            Driver newDriver,
            String reason,
            UserAccount actor,
            LocalDateTime changedAt) {

        ensureDrivingHasNotStarted(
                "The assigned driver cannot be changed after driving has started."
        );

        Objects.requireNonNull(
                newDriver,
                "New driver is required."
        );

        Objects.requireNonNull(
                actor,
                "The driver recording the override is required."
        );

        Objects.requireNonNull(
                changedAt,
                "Override time is required."
        );

        if (!Objects.equals(
                organization.getId(),
                newDriver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "The replacement driver must belong "
                            + "to the same organization."
            );
        }

        if (Objects.equals(
                driver.getId(),
                newDriver.getId()
        )) {
            return false;
        }

        /*
         * Automatic assignments already preserve the original
         * recommendation. Manual assignments do not, so when a
         * driver self-overrides a manual assignment we capture
         * the driver who was scheduled before replacing them.
         */
        if (originallySuggestedDriver == null) {
            originallySuggestedDriver = driver;
        }

        driver = newDriver;
        assignmentSource = AssignmentSource.OVERRIDE;
        this.changedAt = changedAt;
        changedBy = actor;
        changeReason = normalizeReason(reason);

        return true;
    }

    public void startDriving(
            LocalDateTime startedAt,
            UserAccount actor) {

        Objects.requireNonNull(
                startedAt,
                "Actual start time is required."
        );

        if (scheduledStartTime == null
                || scheduledEndTime == null) {

            throw new IllegalStateException(
                    "Set the scheduled start and end time before starting driving."
            );
        }

        if (actualStartedAt != null) {
            throw new IllegalStateException(
                    "Driving has already been started for this assignment."
            );
        }

        if (actualEndedAt != null) {
            throw new IllegalStateException(
                    "This driving assignment has already been completed."
            );
        }

        actualStartedAt = startedAt;
        actualStartedBy = Objects.requireNonNull(
                actor,
                "The account starting driving is required."
        );
    }

    public void endDriving(
            LocalDateTime endedAt,
            UserAccount actor) {

        Objects.requireNonNull(
                endedAt,
                "Actual end time is required."
        );

        if (actualStartedAt == null) {
            throw new IllegalStateException(
                    "Start driving before ending the assignment."
            );
        }

        if (actualEndedAt != null) {
            throw new IllegalStateException(
                    "Driving has already been ended for this assignment."
            );
        }

        if (endedAt.isBefore(actualStartedAt)) {
            throw new IllegalStateException(
                    "Actual end time cannot be before the actual start time."
            );
        }

        actualEndedAt = endedAt;
        actualEndedBy = Objects.requireNonNull(
                actor,
                "The account ending driving is required."
        );
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    private void ensureDrivingHasNotStarted(
            String message) {

        if (actualStartedAt != null) {
            throw new IllegalStateException(message);
        }
    }

    private static String normalizeReason(
            String reason) {

        if (reason == null) {
            return null;
        }

        String cleaned = reason.trim();

        return cleaned.isBlank()
                ? null
                : cleaned;
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

    public Driver getDriver() {
        return driver;
    }

    public Driver getFinalDriver() {
        return driver;
    }

    public Driver getOriginallySuggestedDriver() {
        return originallySuggestedDriver;
    }

    public ScheduleImport getSourceScheduleImport() {
        return sourceScheduleImport;
    }

    public boolean requiresReviewAgainst(
            Long latestScheduleImportId) {

        if (assignmentSource == AssignmentSource.MANUAL) {
            return false;
        }

        if (latestScheduleImportId == null) {
            return true;
        }

        ScheduleImport comparisonImport =
                reviewedScheduleImport == null
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

    public void confirmScheduleReview(
            ScheduleImport scheduleImport,
            UserAccount actor,
            LocalDateTime reviewedAt) {

        ensureDrivingHasNotStarted(
                "Schedule coverage cannot be confirmed after driving has started."
        );

        Objects.requireNonNull(
                scheduleImport,
                "Schedule import is required."
        );

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

    public ScheduleImport getReviewedScheduleImport() {
        return reviewedScheduleImport;
    }

    public LocalDateTime getScheduleReviewedAt() {
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

    public long getScheduledMinutes() {

        if (scheduledStartTime == null
                || scheduledEndTime == null) {
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

    public LocalDateTime getActualStartedAt() {
        return actualStartedAt;
    }

    public UserAccount getActualStartedBy() {
        return actualStartedBy;
    }

    public LocalDateTime getActualEndedAt() {
        return actualEndedAt;
    }

    public UserAccount getActualEndedBy() {
        return actualEndedBy;
    }

    public boolean isDriving() {
        return actualStartedAt != null
                && actualEndedAt == null;
    }

    public boolean isDrivingCompleted() {
        return actualStartedAt != null
                && actualEndedAt != null;
    }

    public long getActualMinutes() {

        if (!isDrivingCompleted()) {
            return 0;
        }

        return Duration.between(
                actualStartedAt,
                actualEndedAt
        ).toMinutes();
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

        return getActualMinutes()
                - getScheduledMinutes();
    }

    public AssignmentSource getAssignmentSource() {
        return assignmentSource;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public UserAccount getChangedBy() {
        return changedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}