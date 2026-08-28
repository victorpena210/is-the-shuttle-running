package com.victorpena.shuttleflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "uploaded_shifts")
public class UploadedShift {

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

    /*
     * This is nullable because shifts that existed before
     * migration V14 do not have a schedule import record.
     * Every new upload created after V14 will populate it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "schedule_import_id",
            updatable = false
    )
    private ScheduleImport scheduleImport;

    @Column(
            name = "employee_id",
            nullable = false,
            length = 50
    )
    private String employeeId;

    @Column(
            name = "employee_name",
            nullable = false,
            length = 200
    )
    private String employeeName;

    @Column(
            nullable = false,
            length = 200
    )
    private String department;

    @Column(
            nullable = false,
            length = 200
    )
    private String job;

    @Column(
            name = "shift_date",
            nullable = false
    )
    private LocalDate shiftDate;

    @Column(
            name = "start_time",
            nullable = false
    )
    private LocalTime startTime;

    @Column(
            name = "end_time",
            nullable = false
    )
    private LocalTime endTime;

    @Column(
            name = "pickup_location",
            nullable = false,
            length = 200
    )
    private String pickupLocation;

    @Column(
            name = "imported_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime importedAt;

    protected UploadedShift() {
    }

    public UploadedShift(
            Organization organization,
            ScheduleImport scheduleImport,
            String employeeId,
            String employeeName,
            String department,
            String job,
            LocalDate shiftDate,
            LocalTime startTime,
            LocalTime endTime,
            String pickupLocation,
            LocalDateTime importedAt) {

        this.organization = organization;
        this.scheduleImport = scheduleImport;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.job = job;
        this.shiftDate = shiftDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.pickupLocation = pickupLocation;
        this.importedAt = importedAt;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public ScheduleImport getScheduleImport() {
        return scheduleImport;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public String getJob() {
        return job;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }
}