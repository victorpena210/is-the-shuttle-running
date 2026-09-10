package technology.renatus.routepulse.calendar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Instant;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;

/**
 * Immutable audit record for a manager decision that the park is operating
 * even though the external operating calendar reported UNKNOWN or CLOSED.
 */
@Entity
@Table(name = "park_calendar_overrides")
public class ParkCalendarOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "operating_date", nullable = false)
    private LocalDate operatingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "calendar_status", nullable = false, length = 16)
    private ParkOperatingStatus calendarStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_status", nullable = false, length = 16)
    private ParkOperatingStatus overrideStatus;

    @Column(name = "calendar_source", length = 80)
    private String calendarSource;

    @Column(name = "diagnostic_code", length = 120)
    private String diagnosticCode;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "confirmed_by_name", nullable = false, length = 220)
    private String confirmedByName;

    @Column(name = "confirmed_by_email", nullable = false, length = 254)
    private String confirmedByEmail;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;

    protected ParkCalendarOverride() {
    }

    public ParkCalendarOverride(
            Long organizationId,
            LocalDate operatingDate,
            ParkOperatingStatus calendarStatus,
            ParkOperatingStatus overrideStatus,
            String calendarSource,
            String diagnosticCode,
            String reason,
            Long confirmedByUserId,
            String confirmedByName,
            String confirmedByEmail,
            Instant confirmedAt) {
        this.organizationId = organizationId;
        this.operatingDate = operatingDate;
        this.calendarStatus = calendarStatus;
        this.overrideStatus = overrideStatus;
        this.calendarSource = calendarSource;
        this.diagnosticCode = diagnosticCode;
        this.reason = reason;
        this.confirmedByUserId = confirmedByUserId;
        this.confirmedByName = confirmedByName;
        this.confirmedByEmail = confirmedByEmail;
        this.confirmedAt = confirmedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public LocalDate getOperatingDate() {
        return operatingDate;
    }

    public ParkOperatingStatus getCalendarStatus() {
        return calendarStatus;
    }

    public ParkOperatingStatus getOverrideStatus() {
        return overrideStatus;
    }

    public String getCalendarSource() {
        return calendarSource;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }

    public String getReason() {
        return reason;
    }

    public Long getConfirmedByUserId() {
        return confirmedByUserId;
    }

    public String getConfirmedByName() {
        return confirmedByName;
    }

    public String getConfirmedByEmail() {
        return confirmedByEmail;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
