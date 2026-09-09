package technology.renatus.routepulse.driving.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.model.Organization;

@Entity
@Table(
        name = "passenger_ride_confirmations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_passenger_ride_assignment_device_window",
                        columnNames = {
                                "assignment_id",
                                "device_token_hash",
                                "ride_window_start"
                        }
                )
        }
)
public class PassengerRideConfirmation {

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

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "assignment_id",
            nullable = false,
            updatable = false
    )
    private DriverAssignment assignment;

    @ManyToOne(
            fetch = FetchType.EAGER,
            optional = false
    )
    @JoinColumn(
            name = "driver_id",
            nullable = false,
            updatable = false
    )
    private Driver driver;

    @Column(
            name = "driver_name_snapshot",
            nullable = false,
            length = 200,
            updatable = false
    )
    private String driverNameSnapshot;

    @Column(
            name = "device_token_hash",
            nullable = false,
            length = 64,
            updatable = false
    )
    private String deviceTokenHash;

    @Column(
            name = "ride_window_start",
            nullable = false,
            updatable = false
    )
    private LocalDateTime rideWindowStart;

    @Column(
            name = "submitted_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime submittedAt;

    protected PassengerRideConfirmation() {
    }

    public PassengerRideConfirmation(
            DriverAssignment assignment,
            Driver actualDriver,
            String deviceTokenHash,
            LocalDateTime rideWindowStart,
            LocalDateTime submittedAt) {

        this.assignment =
                Objects.requireNonNull(
                        assignment,
                        "Driving assignment is required."
                );

        this.organization =
                Objects.requireNonNull(
                        assignment.getOrganization(),
                        "Driving assignment organization is required."
                );

        this.driver =
                Objects.requireNonNull(
                        actualDriver,
                        "Actual driving session driver is required."
                );

        if (!Objects.equals(
                this.organization.getId(),
                this.driver.getOrganization().getId()
        )) {
            throw new IllegalArgumentException(
                    "Actual driver must belong to the assignment organization."
            );
        }

        this.driverNameSnapshot =
                this.driver.getName();

        this.deviceTokenHash =
                requireText(
                        deviceTokenHash,
                        "Device token hash is required."
                );

        this.rideWindowStart =
                Objects.requireNonNull(
                        rideWindowStart,
                        "Ride window start is required."
                );

        this.submittedAt =
                Objects.requireNonNull(
                        submittedAt,
                        "Submission time is required."
                );
    }

    private static String requireText(
            String value,
            String message) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
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

    public String getDriverNameSnapshot() {
        return driverNameSnapshot;
    }

    public String getDeviceTokenHash() {
        return deviceTokenHash;
    }

    public LocalDateTime getRideWindowStart() {
        return rideWindowStart;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
