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
                ),
                @UniqueConstraint(
                        name =
                                "uk_driver_assignments_org_date_driver",
                        columnNames = {
                                "organization_id",
                                "assignment_date",
                                "driver_id"
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

    @Column(
            name = "actual_ended_at"
    )
    private LocalDateTime actualEndedAt;

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

        return new DriverAssignment(
                assignmentDate,
                period,
                suggestedDriver,
                suggestedDriver,
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
            String reason) {

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
        changeReason = normalizeReason(reason);

        return true;
    }

    public void startDriving(
            LocalDateTime startedAt) {

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
    }

    public void endDriving(
            LocalDateTime endedAt) {

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

    public LocalDateTime getActualEndedAt() {
        return actualEndedAt;
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

    public String getChangeReason() {
        return changeReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}