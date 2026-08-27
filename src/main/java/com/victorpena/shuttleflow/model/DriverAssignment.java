package com.victorpena.shuttleflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
                        name = "uk_driver_assignments_org_date_period",
                        columnNames = {
                                "organization_id",
                                "assignment_date",
                                "shuttle_period"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_driver_assignments_org_date_driver",
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @ManyToOne(
            fetch = FetchType.EAGER,
            optional = false
    )
    @JoinColumn(
            name = "driver_id",
            nullable = false
    )
    private Driver driver;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected DriverAssignment() {
    }

    public DriverAssignment(
            LocalDate assignmentDate,
            ShuttlePeriod period,
            Driver driver) {

        this.organization = driver.getOrganization();
        this.assignmentDate = assignmentDate;
        this.period = period;
        this.driver = driver;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}