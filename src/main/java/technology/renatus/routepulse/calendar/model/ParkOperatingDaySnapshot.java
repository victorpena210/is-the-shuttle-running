package technology.renatus.routepulse.calendar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import technology.renatus.routepulse.calendar.domain.ParkOperatingStatus;

@Entity
@Table(
        name = "park_operating_days",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_park_operating_days_park_date",
                columnNames = {
                        "park_id",
                        "operating_date"
                }
        )
)
public class ParkOperatingDaySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "park_id", nullable = false)
    private int parkId;

    @Column(name = "operating_date", nullable = false)
    private LocalDate operatingDate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "operating_status",
            nullable = false,
            length = 16
    )
    private ParkOperatingStatus operatingStatus;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "buyout", nullable = false)
    private boolean buyout;

    @Column(
            name = "source",
            nullable = false,
            length = 80
    )
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "confirmed_by", length = 255)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected ParkOperatingDaySnapshot() {
    }

    public ParkOperatingDaySnapshot(
            int parkId,
            LocalDate operatingDate,
            ParkOperatingStatus operatingStatus,
            LocalTime openingTime,
            LocalTime closingTime,
            boolean buyout,
            String source,
            LocalDateTime fetchedAt) {

        this.parkId = parkId;
        this.operatingDate = operatingDate;
        this.operatingStatus = operatingStatus;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.buyout = buyout;
        this.source = source;
        this.fetchedAt = fetchedAt;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    public void refresh(
            ParkOperatingStatus operatingStatus,
            LocalTime openingTime,
            LocalTime closingTime,
            boolean buyout,
            String source,
            LocalDateTime fetchedAt) {

        this.operatingStatus = operatingStatus;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.buyout = buyout;
        this.source = source;
        this.fetchedAt = fetchedAt;
        this.confirmedBy = null;
        this.confirmedAt = null;
    }

    public void confirmOpen(
            String confirmedBy,
            LocalDateTime confirmedAt) {

        this.operatingStatus = ParkOperatingStatus.OPEN;
        this.openingTime = null;
        this.closingTime = null;
        this.buyout = false;
        this.source = "MANAGER_CONFIRMATION";
        this.fetchedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = confirmedAt;
    }

    public Long getId() {
        return id;
    }

    public int getParkId() {
        return parkId;
    }

    public LocalDate getOperatingDate() {
        return operatingDate;
    }

    public ParkOperatingStatus getOperatingStatus() {
        return operatingStatus;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public boolean isBuyout() {
        return buyout;
    }

    public String getSource() {
        return source;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
